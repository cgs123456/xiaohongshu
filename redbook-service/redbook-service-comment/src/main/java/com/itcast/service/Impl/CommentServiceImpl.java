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
import java.util.HashMap;
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

    /**
     * 评论时间格式（SimpleDateFormat 非线程安全，用 ThreadLocal 复用）
     */
    private static final ThreadLocal<SimpleDateFormat> TIME_FORMATTER =
            ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserClient userClient;

    @Override
    public Result<Void> postComment(CommentDto commentDto) {
        // 身份一律取网关注入的登录态，忽略请求体里的 userId（防止伪造他人身份发评论）
        Integer currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            return Result.failure("用户未登录");
        }
        Comment comment = new Comment();
        comment.setContent(commentDto.getContent());
        comment.setNoteId(commentDto.getNoteId());
        comment.setParentId(commentDto.getParentId() == null ? DEFAULT_PARENT_ID : commentDto.getParentId());
        comment.setUserId(currentUserId);
        comment.setTime(TIME_FORMATTER.get().format(new Date()));
        mongoTemplate.insert(comment);
        return Result.success(null);
    }

    @Override
    public Result<List<CommentVo>> getCommentList(Long noteId, Integer page, Integer pageSize) {
        // 1.一级评论分页（此前一次拉取全部评论，热帖几万条会打爆内存与批量 RPC）
        int p = (page == null || page < 1) ? 1 : page;
        int s = (pageSize == null || pageSize < 1) ? 20 : Math.min(pageSize, 50);
        Query topQuery = new Query(Criteria.where("noteId").is(noteId)
                .and("parentId").is(DEFAULT_PARENT_ID))
                .with(Sort.by(Sort.Direction.DESC, "time"))
                .skip((long) (p - 1) * s)
                .limit(s);
        List<Comment> topOnly = mongoTemplate.find(topQuery, Comment.class);
        if (topOnly.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        // 2.子评论单独查询（不含其他页的一级评论，链断的回复挂回原 parentId，属其他页的楼不重复展示）
        Query childQuery = new Query(Criteria.where("noteId").is(noteId)
                .and("parentId").ne(DEFAULT_PARENT_ID));
        List<Comment> children = mongoTemplate.find(childQuery, Comment.class);

        List<Comment> comments = new ArrayList<>(topOnly.size() + children.size());
        comments.addAll(topOnly);
        comments.addAll(children);
        // 2.批量查询用户信息（一次 Feign 调用，消除循环内 RPC），失败降级为空
        List<Integer> userIds = comments.stream()
                .map(Comment::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Integer, User> userMap = batchGetUsers(userIds);
        // 3.先按 parentId 分组：一级评论（parentId="0"）与其余评论（parentId 指向本次结果中的某条评论）
        Map<String, Comment> byId = new HashMap<>();
        Map<String, List<Comment>> childrenMap = new HashMap<>();
        List<Comment> topComments = new ArrayList<>();
        for (Comment comment : comments) {
            if (comment.getId() != null) {
                byId.put(comment.getId().toHexString(), comment);
            }
            String parentId = comment.getParentId() == null ? DEFAULT_PARENT_ID : comment.getParentId();
            if (DEFAULT_PARENT_ID.equals(parentId)) {
                topComments.add(comment);
            } else {
                childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(comment);
            }
        }
        // 4.把挂在"回复"上的回复向上归到一级评论：沿 parentId 上溯到根（一级）评论
        Map<String, List<Comment>> groupedByRoot = new HashMap<>();
        for (Map.Entry<String, List<Comment>> entry : childrenMap.entrySet()) {
            for (Comment child : entry.getValue()) {
                String rootKey = entry.getKey();
                String topKey = null;
                // 最多上溯 comments.size() 次，防止脏数据成环；topKey 记录上溯链上最后一级评论（即根一级评论）
                int hop = 0;
                while (!DEFAULT_PARENT_ID.equals(rootKey) && byId.containsKey(rootKey) && hop++ <= comments.size()) {
                    topKey = rootKey;
                    rootKey = byId.get(rootKey).getParentId() == null
                            ? DEFAULT_PARENT_ID : byId.get(rootKey).getParentId();
                }
                // 挂到根一级评论下；父链断裂（根不在本次结果中）时挂回原 parentId，避免回复丢失
                String attachKey = topKey != null ? topKey : entry.getKey();
                groupedByRoot.computeIfAbsent(attachKey, k -> new ArrayList<>()).add(child);
            }
        }
        // 5.组装两级树：一级评论按时间倒序（查询已排序），子评论按挂载顺序（保持倒序）
        List<CommentVo> voList = new ArrayList<>(topComments.size());
        for (Comment top : topComments) {
            CommentVo vo = new CommentVo();
            vo.setComment(top);
            vo.setCommentVoId(top.getId() == null ? null : top.getId().toHexString());
            vo.setUser(userMap.get(top.getUserId()));
            vo.setDealTime(dealTime(top.getTime()));
            List<Comment> childList = top.getId() == null ? null : groupedByRoot.get(top.getId().toHexString());
            if (childList != null && !childList.isEmpty()) {
                List<CommentVo> childVos = new ArrayList<>(childList.size());
                for (Comment child : childList) {
                    CommentVo childVo = new CommentVo();
                    childVo.setComment(child);
                    childVo.setCommentVoId(child.getId() == null ? null : child.getId().toHexString());
                    childVo.setUser(userMap.get(child.getUserId()));
                    childVo.setDealTime(dealTime(child.getTime()));
                    childVos.add(childVo);
                }
                vo.setChildrenList(childVos);
            }
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
        Map<Integer, User> userMap = new HashMap<>();
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
