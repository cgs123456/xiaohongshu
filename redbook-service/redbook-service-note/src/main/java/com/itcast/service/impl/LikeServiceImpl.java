package com.itcast.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.google.gson.Gson;
import com.itcast.annotation.SendMessage;
import com.itcast.client.UserClient;
import com.itcast.component.UserBatchLoader;
import com.itcast.constant.ExceptionConstant;
import com.itcast.constant.MqConstant;
import com.itcast.constant.RedisConstant;
import com.itcast.context.UserContext;
import com.itcast.enums.LogType;
import com.itcast.enums.MessageTypeEnum;
import com.itcast.exception.NoteNoExistException;
import com.itcast.mapper.LikeMapper;
import com.itcast.mapper.NoteMapper;
import com.itcast.model.pojo.Like;
import com.itcast.model.pojo.Message;
import com.itcast.model.pojo.Note;
import com.itcast.model.pojo.User;
import com.itcast.model.vo.LikeVo;
import com.itcast.result.Result;
import com.itcast.service.LikeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LikeServiceImpl implements LikeService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private LikeMapper likeMapper;

    @Autowired
    private UserClient userClient;

    @Autowired
    private UserBatchLoader userBatchLoader;

    private final Gson gson = new Gson();

    @Override
    @SendMessage(type = LogType.LIKE)
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> like(Long noteId) {
        Integer userId = UserContext.getUserId();

        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new NoteNoExistException(ExceptionConstant.NOTE_NO_EXIST);
        }

        // 1.判断redis中的本用户是否存在这个id
        Boolean isLike = redisTemplate.opsForValue().getBit(RedisConstant.LIKE_SET_CACHE + noteId, userId);

        if (Boolean.TRUE.equals(isLike)) {
            // 2.取消点赞：删除影响行数为 0 说明本来就没点过赞，直接幂等返回成功
            LambdaQueryWrapper<Like> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Like::getNoteId, noteId).eq(Like::getUserId, userId);
            int rows = likeMapper.delete(queryWrapper);
            if (rows == 0) {
                return Result.success(null);
            }

            // 3.原子更新点赞数并加保护，保证不会被减成负数（避免先查后改的并发丢失更新）
            noteMapper.update(null,
                new LambdaUpdateWrapper<Note>()
                    .eq(Note::getId, noteId)
                    .gt(Note::getLike, 0)
                    .setSql("`like` = `like` - 1")
            );
            log.info("取消点赞: noteId={}, userId={}", noteId, userId);

            // 4.redis 状态位、缓存删除移到事务提交后执行
            runAfterCommit(() -> {
                redisTemplate.opsForValue().setBit(RedisConstant.LIKE_SET_CACHE + noteId, userId, false);
                redisTemplate.delete(RedisConstant.NOTE_DETAIL_CACHE + noteId);
            });
        } else {
            // 5.添加点赞：直接 insert，唯一键冲突视为已点赞（幂等）
            Like like = new Like();
            like.setNoteId(noteId);
            like.setOwnId(note.getUserId());
            like.setUserId(userId);
            like.setCreateTime(new Date());
            try {
                likeMapper.insert(like);
            } catch (DuplicateKeyException e) {
                // 已点赞，幂等返回成功；修复 redis 状态位即可
                log.info("重复点赞，幂等处理: noteId={}, userId={}", noteId, userId);
                runAfterCommit(() -> {
                    redisTemplate.opsForValue().setBit(RedisConstant.LIKE_SET_CACHE + noteId, userId, true);
                    redisTemplate.delete(RedisConstant.NOTE_DETAIL_CACHE + noteId);
                });
                return Result.success(null);
            }

            // 6.原子更新点赞数
            noteMapper.update(null,
                new LambdaUpdateWrapper<Note>()
                    .eq(Note::getId, noteId)
                    .setSql("`like` = `like` + 1")
            );
            log.info("点赞: noteId={}, userId={}", noteId, userId);

            // 7.redis 状态位、缓存删除、MQ 消息发送移到事务提交后执行
            final Like savedLike = like;
            final Note finalNote = note;
            runAfterCommit(() -> {
                redisTemplate.opsForValue().setBit(RedisConstant.LIKE_SET_CACHE + noteId, userId, true);
                redisTemplate.delete(RedisConstant.NOTE_DETAIL_CACHE + noteId);
                try {
                    // 用户点赞，消息发送
                    Message<LikeVo> likeMessage = new Message<>();
                    likeMessage.setType(MessageTypeEnum.LIKE.getCode());
                    likeMessage.setNoticeId(finalNote.getUserId());
                    likeMessage.setObj(LikeVo.builder().like(savedLike).note(finalNote)
                            .user(userClient.getUserById(savedLike.getUserId()).getData()).build());
                    rabbitTemplate.convertAndSend(MqConstant.MESSAGE_NOTICE_EXCHANGE, MqConstant.LIKE_KEY, gson.toJson(likeMessage));
                } catch (Exception e) {
                    log.error("点赞消息发送失败 noteId={}, userId={}", noteId, userId, e);
                }
            });
        }
        return Result.success(null);
    }

    @Override
    public Result<Boolean> isLike(Long noteId) {
        Integer userId = UserContext.getUserId();
        Boolean isLike = redisTemplate.opsForValue().getBit(
                RedisConstant.LIKE_SET_CACHE + noteId, userId);
        return Result.success(isLike);
    }

    @Override
    public Result<List<LikeVo>> getLikeList() {
        Integer userId = UserContext.getUserId();
        // 1.一次性查询点赞记录，避免 N+1
        LambdaQueryWrapper<Like> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Like::getOwnId, userId).orderByDesc(Like::getCreateTime);
        List<Like> likeList = likeMapper.selectList(queryWrapper);
        if (likeList.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        // 2.批量查询笔记
        List<Long> noteIds = likeList.stream().map(Like::getNoteId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, Note> noteMap = noteIds.isEmpty() ? Collections.emptyMap()
                : noteMapper.selectBatchIds(noteIds).stream()
                    .collect(Collectors.toMap(Note::getId, n -> n, (a, b) -> a));
        // 3.批量查询用户
        List<Integer> userIds = likeList.stream().map(Like::getUserId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Integer, User> userMap = userBatchLoader.batchGetUsers(userIds);
        // 4.组装 VO，笔记不存在的跳过该条，绝不让整个列表 500
        List<LikeVo> resultList = new ArrayList<>(likeList.size());
        for (Like like : likeList) {
            Note note = noteMap.get(like.getNoteId());
            if (note == null) {
                continue;
            }
            resultList.add(LikeVo.builder().like(like).note(note).user(userMap.get(like.getUserId())).build());
        }
        return Result.success(resultList);
    }


    /**
     * 事务提交后执行（无事务时直接执行），用于 redis 操作、MQ 发送等
     */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
