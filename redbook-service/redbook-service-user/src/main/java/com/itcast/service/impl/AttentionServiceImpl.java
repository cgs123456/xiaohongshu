package com.itcast.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.itcast.constant.ExceptionConstant;
import com.itcast.constant.MqConstant;
import com.itcast.constant.RedisConstant;
import com.itcast.enums.AttentionTypeEnum;
import com.itcast.enums.MessageTypeEnum;
import com.itcast.exception.UserNoExistException;
import com.itcast.mapper.AttentionMapper;
import com.itcast.mapper.UserMapper;
import com.itcast.model.pojo.Message;
import com.itcast.model.pojo.Attention;
import com.itcast.model.pojo.User;
import com.itcast.result.Result;
import com.itcast.service.AttentionService;
import com.itcast.context.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class AttentionServiceImpl implements AttentionService {

    @Autowired
    private AttentionMapper attentionMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public Result<Integer> isAttention(Integer otherId) {
        int flag;
        // 1.获取登录用户id
        Integer ownId = UserContext.getUserId();
        // 2.判断是否是自己
        if (otherId.intValue() == ownId.intValue()) {
            flag = AttentionTypeEnum.OWN.getCode();
        } else if(Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(RedisConstant.ATTENTION_CACHE + ownId, otherId))) {
            flag = AttentionTypeEnum.ATTENTION.getCode();
        } else {
            flag = AttentionTypeEnum.INATTENTION.getCode();
        }
        return Result.success(flag);
    }

    @Override
    public Result<Void> attention(Integer otherId) {
        // 1.获取登录用户id
        Integer ownId = UserContext.getUserId();
        // 2.禁止关注自己
        if (otherId.intValue() == ownId.intValue()) {
            return Result.failure("不能关注自己");
        }
        // 3.校验被关注用户存在
        User otherUser = userMapper.selectById(otherId);
        if (otherUser == null) {
            throw new UserNoExistException(ExceptionConstant.USER_NO_EXIST);
        }

        String attentionKey = RedisConstant.ATTENTION_CACHE + ownId;
        // 4.判断是否关注过（Redis Set）
        Boolean isAttention = redisTemplate.opsForSet().isMember(attentionKey, otherId);

        if (Boolean.TRUE.equals(isAttention)) {
            // 5.取关：按 ownId + otherId 条件删除
            attentionMapper.delete(Wrappers.<Attention>lambdaQuery()
                    .eq(Attention::getOwnId, ownId)
                    .eq(Attention::getOtherId, otherId));
            // 统一使用 Redis Set：SREM
            redisTemplate.opsForSet().remove(attentionKey, otherId);
        } else {
            // 6.关注：insert 用 DuplicateKeyException 幂等兜底（数据库唯一键稍后添加）
            Attention attention = new Attention();
            attention.setOwnId(ownId);
            attention.setOtherId(otherId);
            attention.setTime(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
            try {
                attentionMapper.insert(attention);
            } catch (DuplicateKeyException e) {
                // 唯一键冲突说明已关注，幂等返回成功
                log.info("重复关注，幂等处理 ownId={} otherId={}", ownId, otherId);
                redisTemplate.opsForSet().add(attentionKey, otherId);
                return Result.success(null);
            }
            // 统一使用 Redis Set：SADD
            redisTemplate.opsForSet().add(attentionKey, otherId);

            // 用户关注，通知消息
            Message attentionMessage = new Message();
            attentionMessage.setType(MessageTypeEnum.ATTENTION.getCode());
            attentionMessage.setNoticeId(otherId);
            attentionMessage.setObj(attention);
            rabbitTemplate.convertAndSend(MqConstant.MESSAGE_NOTICE_EXCHANGE, MqConstant.ATTENTION_KEY, attentionMessage);
        }
        return Result.success(null);
    }

    @Override
    public Result<List<Attention>> getAttention(Integer userId) {
        LambdaQueryWrapper<Attention> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Attention::getOwnId, userId);
        List<Attention> attentionList = attentionMapper.selectList(queryWrapper);
        // 回填 Redis Set（ATTENTION_CACHE + ownId），保证 Redis 重启/数据丢失后 isAttention 仍正确
        if (!attentionList.isEmpty()) {
            Object[] otherIds = attentionList.stream()
                    .map(Attention::getOtherId)
                    .filter(java.util.Objects::nonNull)
                    .toArray();
            if (otherIds.length > 0) {
                redisTemplate.opsForSet().add(RedisConstant.ATTENTION_CACHE + userId, otherIds);
            }
        }
        return Result.success(attentionList);
    }
}
