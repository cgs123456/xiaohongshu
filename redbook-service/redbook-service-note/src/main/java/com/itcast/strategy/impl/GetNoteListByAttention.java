package com.itcast.strategy.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itcast.client.UserClient;
import com.itcast.component.UserBatchLoader;
import com.itcast.constant.RedisConstant;
import com.itcast.context.UserContext;
import com.itcast.mapper.NoteMapper;
import com.itcast.model.pojo.Attention;
import com.itcast.model.pojo.Note;
import com.itcast.model.pojo.User;
import com.itcast.model.vo.NoteVo;
import com.itcast.result.Result;
import com.itcast.strategy.GetNotesStrategy;
import com.itcast.strategy.NoteStrategyContext;
import com.itcast.strategy.NoteStrategyType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注流（推拉结合读侧）：
 * 1. 收件箱（Redis List，发布时扇出写入）按页读取 + 批量回源笔记；
 * 2. 读侧过滤：已取关作者（收件箱不清理，读时按当前关注关系裁剪）、待审核/已删除笔记；
 * 3. 拉取兜底：收件箱耗尽（翻页超箱深/扇出失败/历史内容）回退 DB 查询关注作者笔记。
 * 收件箱是加速层而非事实源——任何 Redis 异常都退化为纯拉模式。
 */
@Component
@Slf4j
public class GetNoteListByAttention implements GetNotesStrategy {

    @Autowired
    private UserClient userClient;

    @Autowired
    private UserBatchLoader userBatchLoader;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public NoteStrategyType getStrategyType() {
        return NoteStrategyType.BY_ATTENTION;
    }

    @Override
    public Result<List<NoteVo>> getNotes(NoteStrategyContext context) {
        Integer userId = UserContext.getUserId();
        // 1.当前关注集合（读侧过滤已取关作者）。
        // getAttention 上限 500 条：刚好触顶说明关注关系可能被截断，跳过过滤——
        // 宁可漏进几条已取关作者的笔记，也不能把第 501 位之后的关注者内容全过滤掉
        List<Attention> attentions = userClient.getAttention(userId).getData();
        if (attentions == null || attentions.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        boolean followingCapped = attentions.size() >= 500;
        Set<Integer> followingIds = followingCapped
                ? java.util.Collections.emptySet()
                : attentions.stream().map(Attention::getOtherId)
                        .filter(Objects::nonNull).collect(Collectors.toSet());
        if (followingIds.isEmpty() && !followingCapped) {
            return Result.success(new ArrayList<>());
        }

        int page = page(context);
        int pageSize = pageSize(context);

        // 2.收件箱优先读取（capped 时 followingIds 为 null = 跳过取关过滤）
        List<Note> noteList = readFromInbox(userId, followingCapped ? null : followingIds, page, pageSize);

        // 3.收件箱不足一页（箱深耗尽/无数据）→ DB 拉取兜底补齐
        if (noteList.size() < pageSize) {
            noteList = pullFromDb(followingIds, page, pageSize);
        }

        if (noteList.isEmpty()) {
            return Result.success(new ArrayList<>());
        }

        // 4.批量查询用户并组装 VO
        Map<Integer, User> userMap = userBatchLoader.batchGetUsers(
                noteList.stream().map(Note::getUserId).filter(Objects::nonNull).distinct().collect(Collectors.toList()));
        List<NoteVo> resultList = new ArrayList<>(noteList.size());
        for (Note note : noteList) {
            NoteVo noteVo = new NoteVo();
            noteVo.setNote(note);
            noteVo.setUser(userMap.get(note.getUserId()));
            resultList.add(noteVo);
        }
        return Result.success(resultList);
    }

    /** 收件箱按页读取（LPUSH 最新在前，LRANGE 天然按时间倒序），过滤取关/审核中/已删除；followingIds 为 null 时跳过取关过滤 */
    private List<Note> readFromInbox(Integer userId, Set<Integer> followingIds, int page, int pageSize) {
        try {
            String inboxKey = RedisConstant.FEED_INBOX + userId;
            Long size = redisTemplate.opsForList().size(inboxKey);
            if (size == null || size == 0) {
                return new ArrayList<>();
            }
            int start = (page - 1) * pageSize;
            if (start >= size) {
                return new ArrayList<>(); // 翻页超出箱深，交给拉取兜底
            }
            List<Object> ids = redisTemplate.opsForList().range(inboxKey, start, start + pageSize - 1L);
            if (ids == null || ids.isEmpty()) {
                return new ArrayList<>();
            }
            List<Long> noteIds = ids.stream()
                    .filter(Objects::nonNull)
                    .map(id -> Long.valueOf(id.toString()))
                    .distinct()
                    .collect(Collectors.toList());
            if (noteIds.isEmpty()) {
                return new ArrayList<>();
            }
            List<Note> notes = noteMapper.selectBatchIds(noteIds).stream()
                    // 已删除（审核拒绝）的笔记 selectBatchIds 查不到，自然过滤；
                    // 待审核的不进关注流；取关作者的不展示
                    .filter(n -> n.getModerationStatus() == null
                            || !"PENDING_REVIEW".equals(n.getModerationStatus()))
                    .filter(n -> followingIds == null || followingIds.contains(n.getUserId()))
                    .sorted((a, b) -> Long.compare(b.getId(), a.getId())) // 收件箱顺序在批量回源后恢复
                    .collect(Collectors.toList());
            return notes;
        } catch (Exception e) {
            log.warn("收件箱读取失败，退化为拉模式，userId={}", userId, e);
            return new ArrayList<>();
        }
    }

    /** DB 拉取兜底：关注作者的全部笔记按时间倒序分页（与原实现一致，含审核过滤） */
    private List<Note> pullFromDb(Set<Integer> followingIds, int page, int pageSize) {
        LambdaQueryWrapper<Note> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(Note::getUserId, followingIds)
                .and(w -> w.isNull(Note::getModerationStatus)
                        .or().ne(Note::getModerationStatus, "PENDING_REVIEW"))
                .orderByDesc(Note::getTime)
                .last("LIMIT " + (page - 1) * pageSize + "," + pageSize);
        return noteMapper.selectList(queryWrapper);
    }

    private int page(NoteStrategyContext context) {
        if (context == null || context.getPage() == null || context.getPage() < 1) {
            return 1;
        }
        return context.getPage();
    }

    private int pageSize(NoteStrategyContext context) {
        int size = (context == null || context.getPageSize() == null) ? 20 : context.getPageSize();
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
