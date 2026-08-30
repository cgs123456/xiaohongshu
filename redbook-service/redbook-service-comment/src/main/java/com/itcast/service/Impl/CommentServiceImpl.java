package com.itcast.service.impl;

import com.itcast.client.UserClient;
import com.itcast.context.UserContext;
import com.itcast.model.dto.CommentDto;
import com.itcast.model.pojo.Comment;
import com.itcast.model.pojo.User;
import com.itcast.model.vo.CommentVo;
import com.itcast.result.Result;
import com.itcast.service.CommentService;
import com.itcast.util.DealTimeUtil;
import com.itcast.util.DiffDayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 评论服务实现（MongoDB 存储，rb_comment 集合）
 */
@Service
@Slf4j
public class CommentServiceImpl implements CommentService {

    /**
     * 默认父评论id（0 代表一级评论）
     */
    private static final String DEFAULT_PARENT_ID = "0";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserClient userClient;

    @Override
    public Result<Void> postComment(CommentDto commentDto) {
        Comment comment = new Comment();
        comment.setContent(commentDto.getContent());
        comment.setNoteId(commentDto.getNoteId());
        comment.setParentId(commentDto.getParentId() == null ? DEFAULT_PARENT_ID : commentDto.getParentId());
        comment.setUserId(commentDto.getUserId() != null ? commentDto.getUserId() : UserContext.getUserId());
        comment.setTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        mongoTemplate.insert(comment);
        return Result.success(null);
    }

    @Override
    public Result<List<CommentVo>> getCommentList(Long noteId) {
        // 1.按笔记查询，按时间倒序
        Query query = new Query(Criteria.where("noteId").is(noteId))
                .with(Sort.by(Sort.Direction.DESC, "time"));
        List<Comment> comments = mongoTemplate.find(query, Comment.class);
        // 2.批量查询用户信息（一次 Feign 调用，消除循环内 RPC），失败降级为空
        List<Integer> userIds = comments.stream()
                .map(Comment::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, User> userMap = batchGetUsers(userIds);
        // 3.组装 VO
        List<CommentVo> voList = new ArrayList<>(comments.size());
        for (Comment comment : comments) {
            CommentVo vo = new CommentVo();
            vo.setComment(comment);
            vo.setUser(userMap.get(comment.getUserId()));
            vo.setDealTime(dealTime(comment.getTime()));
            voList.add(vo);
        }
        return Result.success(voList);
    }

    @Override
    public Result<Integer> getCommentCount(Long noteId) {
        long count = mongoTemplate.count(new Query(Criteria.where("noteId").is(noteId)), Comment.class);
        return Result.success((int) count);
    }

    /**
     * 批量获取评论用户信息，失败时降级为逐个查询，再失败返回空 Map
     */
    private Map<Integer, User> batchGetUsers(List<Integer> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            String ids = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            Result<List<User>> result = userClient.getUserByIds(ids);
            List<User> users = result.getData();
            if (users != null && !users.isEmpty()) {
                return users.stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
            }
        } catch (Exception e) {
            log.error("批量获取评论用户信息失败，降级为逐个查询", e);
        }
        Map<Integer, User> userMap = new java.util.HashMap<>();
        for (Integer uid : userIds) {
            try {
                Result<User> result = userClient.getUserById(uid);
                if (result != null && result.getData() != null) {
                    userMap.put(uid, result.getData());
                }
            } catch (Exception e) {
                log.error("获取评论用户信息失败 userId={}", uid, e);
            }
        }
        return userMap;
    }

    /**
     * 处理时间展示（如：3天前），解析失败降级返回原始时间
     */
    private String dealTime(String time) {
        if (time == null || time.isEmpty()) {
            return "";
        }
        try {
            Date commentTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(time);
            return DealTimeUtil.dealTime(DiffDayUtil.diffDays(commentTime, new Date()));
        } catch (ParseException e) {
            return time;
        }
    }
}
