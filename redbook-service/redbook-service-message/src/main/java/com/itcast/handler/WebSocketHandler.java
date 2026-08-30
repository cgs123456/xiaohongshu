package com.itcast.handler;

import com.auth0.jwt.exceptions.TokenExpiredException;
import com.itcast.session.Session;
import com.itcast.util.JwtUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ChannelHandler.Sharable
@Slf4j
public class WebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    /** 离线通知列表 key 前缀（与 MessageNoticeConsumer 保持一致） */
    private static final String OFFLINE_NOTICE_KEY = "offline:notice:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame msg) {
        String message = msg.text();

        // 处理心跳消息
        if ("ping".equals(message)) {
            ctx.channel().writeAndFlush(new TextWebSocketFrame("pong"));
            log.debug("收到心跳ping，返回pong");
            return;
        }

        // 处理token认证
        if (!StringUtils.isBlank(message)) {
            try {
                Integer userId = Integer.valueOf(JwtUtil.parseToken(message));
                Session.bind(userId, ctx.channel());
                ctx.channel().writeAndFlush(new TextWebSocketFrame("auth_success"));
                log.info("用户登录，登录用户的ID为：{}", userId);
                // 认证成功后推送离线期间积压的通知
                sendOfflineNotices(userId, ctx.channel());
            } catch (TokenExpiredException e) {
                log.error("用户token过期，请重新认证后重试");
                ctx.channel().writeAndFlush(new TextWebSocketFrame("auth_failed:token_expired"));
            } catch (Exception e) {
                log.error("用户认证失败：{}", e.getMessage());
                ctx.channel().writeAndFlush(new TextWebSocketFrame("auth_failed:invalid_token"));
            }
        }
    }

    /**
     * 推送离线期间积压的通知：读取 offline:notice:{userId} 列表逐条发送，发完删除
     */
    private void sendOfflineNotices(Integer userId, Channel channel) {
        try {
            String key = OFFLINE_NOTICE_KEY + userId;
            List<String> offlineNotices = stringRedisTemplate.opsForList().range(key, 0, -1);
            if (offlineNotices != null && !offlineNotices.isEmpty()) {
                for (String notice : offlineNotices) {
                    channel.writeAndFlush(new TextWebSocketFrame(notice)).addListener(future -> {
                        if (!future.isSuccess()) {
                            log.error("发送离线通知失败，userId={}", userId, future.cause());
                        }
                    });
                }
                stringRedisTemplate.delete(key);
                log.info("已推送 {} 条离线通知，userId={}", offlineNotices.size(), userId);
            }
        } catch (Exception e) {
            log.error("推送离线通知失败，userId={}", userId, e);
        }
    }

    /**
     * 处理空闲事件：读空闲（60秒无数据且无心跳）时关闭连接
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleStateEvent = (IdleStateEvent) evt;
            if (idleStateEvent.state() == IdleState.READER_IDLE) {
                log.warn("连接读空闲超过60秒，关闭连接，channel={}", ctx.channel());
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx){
        Channel channel = ctx.channel();
        Integer userId = Session.getUserId(channel);
        if(channel != null && userId != null) {
            Session.unbind(userId, ctx.channel());
            log.info("用户断开连接，用户ID：{}", userId);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("WebSocket异常：{}", cause.getMessage());
        ctx.close();
    }
}
