package com.itcast.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

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
    public Result<Void> collection(Long noteId) {
        Integer userId = UserContext.getUserId();
        // 1.判断redis中的本用户是否存在这个id
        Boolean isCollection = redisTemplate.opsForValue().getBit(RedisConstant.COLLECTION_SET_CACHE + noteId, userId);
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new NoteNoExistException(ExceptionConstant.NOTE_NO_EXIST);
        }
        
        if (Boolean.TRUE.equals(isCollection)) {
            // 删除收藏 - 加上 userId 条件
            LambdaQueryWrapper<Collection> queryWrapper = new LambdaQueryWrapper<Collection>()
                    .eq(Collection::getNoteId, noteId)
                    .eq(Collection::getUserId, userId);
            collectionMapper.delete(queryWrapper);
            // 更新redis
            redisTemplate.opsForValue().setBit(RedisConstant.COLLECTION_SET_CACHE + noteId, userId, false);
            // 原子更新收藏数：collection = collection - 1
            noteMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Note>()
                    .eq(Note::getId, noteId)
                    .setSql("collection = collection - 1")
            );
            log.info("取消收藏: noteId={}, userId={}", noteId, userId);
        } else {
            // 添加收藏
            Collection collection = new Collection();
            collection.setNoteId(noteId);
            collection.setUserId(userId);
            collectionMapper.insert(collection);
            // 更新redis
            redisTemplate.opsForValue().setBit(RedisConstant.COLLECTION_SET_CACHE + noteId, userId, true);
            // 原子更新收藏数：collection = collection + 1
            noteMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Note>()
                    .eq(Note::getId, noteId)
                    .setSql("collection = collection + 1")
            );
            log.info("收藏: noteId={}, userId={}", noteId, userId);
            // 用户收藏，消息发送
            Message collectionMessage = new Message();
            collectionMessage.setType(MessageTypeEnum.COLLECTION.getCode());
            collectionMessage.setNoticeId(note.getUserId());
            collectionMessage.setObj(collection);
            rabbitTemplate.convertAndSend(MqConstant.MESSAGE_NOTICE_EXCHANGE, MqConstant.COLLECTION_KEY, collectionMessage);
        }
        
        // 2.删除笔记缓存
        redisTemplate.delete(RedisConstant.NOTE_DETAIL_CACHE + noteId);
        return Result.success(null);
    }
}
