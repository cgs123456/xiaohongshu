package com.itcast.mq;

import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * MQ 消费者公共辅助：手动 ack/nack、Redis 重试计数、指数退避。
 * 此前 Order/Checkout/Product 三个消费者各自复制一份（约 120 行三份拷贝），
 * 改重试策略需同步三处易漂移，收敛于此。
 */
@Slf4j
public final class MqConsumerSupport {

    /** 重试计数 key 的统一 TTL（24h，覆盖最长重试窗口后自清） */
    private static final long RETRY_TTL_HOURS = 24;

    private MqConsumerSupport() {
    }

    public static void safeAck(Channel channel, long deliveryTag, String what) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("{} 确认消息异常", what, ex);
        }
    }

    public static void safeNack(Channel channel, long deliveryTag, boolean requeue, String what) {
        try {
            channel.basicNack(deliveryTag, false, requeue);
        } catch (Exception ex) {
            log.error("{} 拒绝消息异常", what, ex);
        }
    }

    /**
     * 递增 messageId 的重试计数（首建设 TTL）。
     * @return 当前重试次数；Redis 异常按 1 处理（宁可多重试一次也不误判超限）
     */
    public static long nextRetryCount(RedisTemplate<String, Object> redisTemplate,
                                      String keyPrefix, String messageId) {
        try {
            String key = keyPrefix + messageId;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, RETRY_TTL_HOURS, TimeUnit.HOURS);
            }
            return count == null ? 1 : count;
        } catch (Exception ex) {
            log.error("{} 记录重试次数失败，messageId={}", keyPrefix, messageId, ex);
            return 1;
        }
    }

    /** 指数退避：1s/2s/4s...（封顶 8s），避免立即重入队形成毫秒级重试风暴 */
    public static void sleepBackoff(long retryCount) {
        try {
            Thread.sleep(Math.min(1000L * (1L << (retryCount - 1)), 8000));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
