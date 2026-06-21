package com.itcast.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.google.gson.Gson;
import com.itcast.annotation.SendMessage;
import com.itcast.client.UserClient;
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
import com.itcast.model.vo.LikeVo;
import com.itcast.result.Result;
import com.itcast.service.LikeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;
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

    private final Gson gson = new Gson();

    @Override
    @SendMessage(type = LogType.LIKE)
    @Transactional
    public Result<Void> like(Long noteId) {
        Integer userId = UserContext.getUserId();

        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new NoteNoExistException(ExceptionConstant.NOTE_NO_EXIST);
        }

        // 1.判断redis中的本用户是否存在这个id
        Boolean isLike = redisTemplate.opsForValue().getBit(RedisConstant.LIKE_SET_CACHE + noteId, userId);

        if (Boolean.TRUE.equals(isLike)) {
            // 删除点赞 - 加上 userId 条件
            LambdaQueryWrapper<Like> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Like::getNoteId, noteId).eq(Like::getUserId, userId);
            likeMapper.delete(queryWrapper);

            // 更新 redis
            redisTemplate.opsForValue().setBit(RedisConstant.LIKE_SET_CACHE + noteId, userId, false);

            // 原子更新点赞数：like_count = like_count - 1
            noteMapper.update(null, 
                new LambdaUpdateWrapper<Note>()
                    .eq(Note::getId, noteId)
                    .setSql("`like` = `like` - 1")
            );
            log.info("取消点赞: noteId={}, userId={}", noteId, userId);
        } else {
            // 添加点赞
            Like like = new Like();
            like.setNoteId(noteId);
            like.setOwnId(note.getUserId());
            like.setUserId(userId);
            like.setCreateTime(new Date());
            likeMapper.insert(like);

            // 更新 redis
            redisTemplate.opsForValue().setBit(RedisConstant.LIKE_SET_CACHE + noteId, userId, true);

            // 原子更新点赞数：like_count = like_count + 1
            noteMapper.update(null, 
                new LambdaUpdateWrapper<Note>()
                    .eq(Note::getId, noteId)
                    .setSql("`like` = `like` + 1")
            );
            log.info("点赞: noteId={}, userId={}", noteId, userId);

            // 用户点赞，消息发送
            Message<LikeVo> likeMessage = new Message<>();
            likeMessage.setType(MessageTypeEnum.LIKE.getCode());
            likeMessage.setNoticeId(note.getUserId());
            likeMessage.setObj(LikeVo.builder().like(like).note(note).user(userClient.getUserById(like.getUserId()).getData()).build());

            // 转换成 json 字符串
            String likeMessageJson = gson.toJson(likeMessage);

            // 发送给 MQ
            rabbitTemplate.convertAndSend(MqConstant.MESSAGE_NOTICE_EXCHANGE, MqConstant.LIKE_KEY, likeMessageJson);
        }
        
        // 2.删除笔记缓存
        redisTemplate.delete(RedisConstant.NOTE_DETAIL_CACHE + noteId);
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
        LambdaQueryWrapper<Like> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Like::getOwnId, userId);
        List<Like> likeList = likeMapper.selectList(queryWrapper);
        if (!likeList.isEmpty()) {
            return Result.success(likeList.stream().map(like -> LikeVo.builder()
                    .like(like)
                    .note(noteMapper.selectById(like.getNoteId()))
                    .user(userClient.getUserById(like.getUserId()).getData())
                    .build()
            ).collect(Collectors.toList()));
        }
        return Result.success(java.util.Collections.emptyList());
    }
}
