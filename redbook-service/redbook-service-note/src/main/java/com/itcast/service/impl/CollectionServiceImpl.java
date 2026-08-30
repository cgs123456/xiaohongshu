package com.itcast.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.itcast.constant.ExceptionConstant;
import com.itcast.constant.MqConstant;
import com.itcast.constant.RedisConstant;
import com.itcast.enums.MessageTypeEnum;
import com.itcast.exception.NoteNoExistException;
import com.itcast.mapper.CollectionMapper;
import com.itcast.mapper.NoteMapper;
import com.itcast.model.pojo.Message;
import com.itcast.model.pojo.Collection;
import com.itcast.model.pojo.Note;
import com.itcast.result.Result;
import com.itcast.service.CollectionService;
import com.itcast.context.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Slf4j
public class CollectionServiceImpl implements CollectionService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private NoteMapper noteMapper;

    @Autowired
    private CollectionMapper collectionMapper;

    @Override
    public Result<Boolean> isCollection(Long noteId) {
        Integer userId = UserContext.getUserId();
        Boolean isCollection = redisTemplate.opsForValue().getBit(
                RedisConstant.COLLECTION_SET_CACHE + noteId, userId);
        return Result.success(isCollection);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> collection(Long noteId) {
        Integer userId = UserContext.getUserId();
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new NoteNoExistException(ExceptionConstant.NOTE_NO_EXIST);
        }

        // 1.判断redis中的本用户是否存在这个id
        Boolean isCollection = redisTemplate.opsForValue().getBit(RedisConstant.COLLECTION_SET_CACHE + noteId, userId);

        if (Boolean.TRUE.equals(isCollection)) {
            // 2.取消收藏：删除影响行数为 0 说明本来就没收藏，直接幂等返回成功
            LambdaQueryWrapper<Collection> queryWrapper = new LambdaQueryWrapper<Collection>()
                    .eq(Collection::getNoteId, noteId)
                    .eq(Collection::getUserId, userId);
            int rows = collectionMapper.delete(queryWrapper);
            if (rows == 0) {
                return Result.success(null);
            }
            // 3.原子更新收藏数并加保护，保证不会被减成负数（避免先查后改的并发丢失更新）
            noteMapper.update(null,
                new LambdaUpdateWrapper<Note>()
                    .eq(Note::getId, noteId)
                    .gt(Note::getCollection, 0)
                    .setSql("collection = collection - 1")
            );
            log.info("取消收藏: noteId={}, userId={}", noteId, userId);
            // 4.redis 状态位、缓存删除移到事务提交后执行
            runAfterCommit(() -> {
                redisTemplate.opsForValue().setBit(RedisConstant.COLLECTION_SET_CACHE + noteId, userId, false);
                redisTemplate.delete(RedisConstant.NOTE_DETAIL_CACHE + noteId);
            });
        } else {
            // 5.添加收藏：直接 insert，唯一键冲突视为已收藏（幂等）
            Collection collection = new Collection();
            collection.setNoteId(noteId);
            collection.setUserId(userId);
            try {
                collectionMapper.insert(collection);
            } catch (DuplicateKeyException e) {
                // 已收藏，幂等返回成功；修复 redis 状态位即可
                log.info("重复收藏，幂等处理: noteId={}, userId={}", noteId, userId);
                runAfterCommit(() -> {
                    redisTemplate.opsForValue().setBit(RedisConstant.COLLECTION_SET_CACHE + noteId, userId, true);
                    redisTemplate.delete(RedisConstant.NOTE_DETAIL_CACHE + noteId);
                });
                return Result.success(null);
            }
            // 6.原子更新收藏数
            noteMapper.update(null,
                new LambdaUpdateWrapper<Note>()
                    .eq(Note::getId, noteId)
                    .setSql("collection = collection + 1")
            );
            log.info("收藏: noteId={}, userId={}", noteId, userId);
            // 7.redis 状态位、缓存删除、MQ 消息发送移到事务提交后执行
            final Collection savedCollection = collection;
            final Note finalNote = note;
            runAfterCommit(() -> {
                redisTemplate.opsForValue().setBit(RedisConstant.COLLECTION_SET_CACHE + noteId, userId, true);
                redisTemplate.delete(RedisConstant.NOTE_DETAIL_CACHE + noteId);
                try {
                    // 用户收藏，消息发送
                    Message collectionMessage = new Message();
                    collectionMessage.setType(MessageTypeEnum.COLLECTION.getCode());
                    collectionMessage.setNoticeId(finalNote.getUserId());
                    collectionMessage.setObj(savedCollection);
                    rabbitTemplate.convertAndSend(MqConstant.MESSAGE_NOTICE_EXCHANGE, MqConstant.COLLECTION_KEY, collectionMessage);
                } catch (Exception e) {
                    log.error("收藏消息发送失败 noteId={}, userId={}", noteId, userId, e);
                }
            });
        }
        return Result.success(null);
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
