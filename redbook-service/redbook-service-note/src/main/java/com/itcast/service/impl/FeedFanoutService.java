package com.itcast.service.impl;

import com.itcast.client.UserClient;
import com.itcast.constant.RedisConstant;
import com.itcast.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 关注流收件箱（推拉结合的推侧）：笔记发布后把 noteId 推送到作者与全部粉丝的收件箱。
 * - 收件箱为 Redis List（LPUSH 最新在前），每箱 LTRIM 保留最近 1000 条——超长历史由拉侧（DB 查询）兜底；
 * - 扇出异步执行且任何失败只记日志：收件箱是加速层不是事实源，读侧永远有 DB 兜底；
 * - 取关不清理收件箱，读侧按当前关注关系过滤（清理代价高于读侧一次 Set 判断）。
 */
@Service
@Slf4j
public class FeedFanoutService {

    /** 收件箱容量（条） */
    private static final long INBOX_SIZE = 1000;
    /** 粉丝分页大小 */
    private static final int FOLLOWER_PAGE_SIZE = 500;

    @Autowired
    private UserClient userClient;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void fanout(Long noteId, Integer authorId) {
        if (noteId == null || authorId == null) {
            return;
        }
        try {
            push(RedisConstant.FEED_INBOX + authorId, noteId);
            int page = 1;
            while (true) {
                Result<List<Integer>> resp = userClient.getFollowerIds(authorId, page, FOLLOWER_PAGE_SIZE);
                List<Integer> followers = resp == null ? null : resp.getData();
                if (followers == null || followers.isEmpty()) {
                    break;
                }
                for (Integer followerId : followers) {
                    push(RedisConstant.FEED_INBOX + followerId, noteId);
                }
                if (followers.size() < FOLLOWER_PAGE_SIZE) {
                    break;
                }
                page++;
            }
            log.info("发布扇出完成，noteId={} authorId={}", noteId, authorId);
        } catch (Exception e) {
            // 收件箱缺失只影响新鲜度，读侧有 DB 拉取兜底
            log.warn("发布扇出失败（读侧拉模式兜底），noteId={} authorId={}", noteId, authorId, e);
        }
    }

    private void push(String inboxKey, Long noteId) {
        try {
            redisTemplate.opsForList().leftPush(inboxKey, noteId);
            redisTemplate.opsForList().trim(inboxKey, 0, INBOX_SIZE - 1);
        } catch (Exception e) {
            log.warn("收件箱推送失败，inbox={} noteId={}", inboxKey, noteId, e);
        }
    }
}
