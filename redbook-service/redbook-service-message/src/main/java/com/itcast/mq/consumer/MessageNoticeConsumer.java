package com.itcast.mq.consumer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.itcast.constant.MqConstant;
import com.itcast.model.pojo.Attention;
import com.itcast.model.pojo.Collection;
import com.itcast.model.pojo.LikeVo;
import com.itcast.model.pojo.Message;
import com.itcast.session.Session;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import io.netty.channel.ChannelFutureListener;
import jakarta.annotation.Resource;

import java.lang.reflect.Type;
import java.time.Duration;

@Component
@Slf4j
public class MessageNoticeConsumer {

    private final Gson gson = new Gson();

    /** 离线通知列表 key 前缀（与 WebSocketHandler 保持一致） */
    private static final String OFFLINE_NOTICE_KEY = "offline:notice:";

    /** 离线通知保留时长 */
    private static final Duration OFFLINE_NOTICE_TTL = Duration.ofDays(7);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = {MqConstant.LOGIN_NOTICE_QUEUE})
    public void loginNotice(Integer userId) {
        log.info("登录用户的ID：{}", userId);
    }

    @RabbitListener(queues = MqConstant.ATTENTION_NOTICE_QUEUE)
    public void attentionNotice(Message<Attention> message) {
        Attention attention = message.getObj();
        String json = gson.toJson(message);
        sendOrStoreOffline(message.getNoticeId(), json,
                "关注用户的ID：{}, 关注的人ID：{}", attention.getOwnId(), attention.getOtherId());
    }

    @RabbitListener(queues = MqConstant.LIKE_NOTICE_QUEUE)
    public void likeNotice(String messageJson) {
        try {
            log.info("收到点赞消息JSON: {}", messageJson);
            // 使用 TypeToken 正确解析泛型类型
            Type messageType = new TypeToken<Message<LikeVo>>(){}.getType();
            Message<LikeVo> message = gson.fromJson(messageJson, messageType);
            LikeVo likeVo = message.getObj();
            sendOrStoreOffline(message.getNoticeId(), messageJson,
                    "点赞用户的ID：{}, 点赞的笔记ID：{}", likeVo.getLike().getUserId(), likeVo.getLike().getNoteId());
        } catch (Exception e) {
            log.error("处理点赞消息失败", e);
        }
    }

    @RabbitListener(queues = MqConstant.COLLECTION_NOTICE_QUEUE)
    public void collectionNotice(Message<Collection> message) {
        Collection collection = message.getObj();
        String json = gson.toJson(message);
        sendOrStoreOffline(message.getNoticeId(), json,
                "收藏用户的ID：{}, 收藏的笔记ID：{}", collection.getUserId(), collection.getNoteId());
    }

    /**
     * 在线直接推送；离线则写入 Redis list 等用户上线时补发（WebSocketHandler 认证成功后读取），
     * 避免通知被消费掉却没送达。
     */
    private void sendOrStoreOffline(Integer noticeId, String json, String logTemplate, Object... args) {
        Channel channel = Session.getChannel(noticeId);
        if (channel == null) {
            try {
                String key = OFFLINE_NOTICE_KEY + noticeId;
                stringRedisTemplate.opsForList().rightPush(key, json);
                stringRedisTemplate.expire(key, OFFLINE_NOTICE_TTL);
                log.info("用户 {} 不在线，通知已存入离线列表", noticeId);
            } catch (Exception e) {
                log.error("离线通知存入 Redis 失败，userId={}", noticeId, e);
            }
            return;
        }
        log.info(logTemplate, args);
        channel.writeAndFlush(new TextWebSocketFrame(json)).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                log.error("通知推送失败，userId={}", noticeId, future.cause());
            }
        });
    }
}
