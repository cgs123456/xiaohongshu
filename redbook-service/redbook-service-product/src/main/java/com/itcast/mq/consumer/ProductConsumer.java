package com.itcast.mq.consumer;

import com.itcast.constant.MqConstant;
import com.itcast.model.dto.BuyDto;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 库存同步消费者（Redis 预扣后的 MySQL 落库）。
 * 与 order 服务消费者相同的异常分类策略：
 * 参数毒消息 → 直接死信；瞬时故障 → 指数退避重试 3 次 → 死信（消息保留在死信队列待人工重放）。
 * 此前 AUTO ack 下抛异常默认无限 requeue：持久故障形成毫秒级重试风暴并阻塞队头。
 */
@Component
@Slf4j
public class ProductConsumer {

    private static final int MAX_RETRY_COUNT = 3;
    private static final String RETRY_KEY = "stock:retry:";

    @Autowired
    private StockSyncService stockSyncService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @RabbitListener(queues = MqConstant.UPDATE_PRODUCT_STOCK_QUEUE)
    public void onMessage(Message<BuyDto> message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                          @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        BuyDto buyDto = message.getPayload();
        if (messageId == null || messageId.isBlank()) {
            // 无 messageId 无法计数重试，视为毒消息直接死信（消息保留在死信队列）
            log.error("库存消息缺少 messageId，转入死信，payload={}", buyDto);
            safeNack(channel, deliveryTag, false);
            return;
        }

        try {
            apply(buyDto);
            safeAck(channel, deliveryTag);
        } catch (IllegalArgumentException e) {
            log.error("库存消息参数非法，转入死信，messageId={}", messageId, e);
            redisTemplate.delete(RETRY_KEY + messageId);
            safeNack(channel, deliveryTag, false);
        } catch (Exception e) {
            long retryCount = nextRetryCount(messageId);
            if (retryCount > MAX_RETRY_COUNT) {
                log.error("CRITICAL: 库存消息重试超限进死信（MySQL 库存与 Redis 疑似漂移，需人工核对重放），messageId={} payload={}",
                        messageId, buyDto, e);
                redisTemplate.delete(RETRY_KEY + messageId);
                safeNack(channel, deliveryTag, false);
            } else {
                log.error("库存消息处理异常，退避重试，messageId={} 第 {} 次", messageId, retryCount, e);
                sleepBackoff(retryCount);
                safeNack(channel, deliveryTag, true);
            }
        }
    }

    /** 库存死信：消息保留在死信队列，这里只做 CRITICAL 日志提醒（重放经 rabbitmq 管理端） */
    @RabbitListener(queues = MqConstant.UPDATE_PRODUCT_STOCK_DEAD_QUEUE)
    public void onDeadLetter(org.springframework.amqp.core.Message message, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        String body = message.getBody() == null ? "" : new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        log.error("CRITICAL: 库存死信需人工核对重放，payload={}", body);
        safeAck(channel, deliveryTag);
    }

    private void apply(BuyDto buyDto) {
        Integer productId = buyDto.getProductId();
        int quantity = buyDto.getQuantity() == null ? 1 : buyDto.getQuantity();
        if (buyDto.getSkuId() == null && productId == null) {
            throw new IllegalArgumentException("库存消息缺少 skuId/productId");
        }

        // 补偿消息：MQ 发送失败时补发的回滚指令，直接回补 MySQL 两级库存
        if (Boolean.TRUE.equals(buyDto.getRestore())) {
            log.info("补偿回补商品库存，skuId={} quantity={}", buyDto.getSkuId(), quantity);
            stockSyncService.restore(buyDto.getSkuId(), productId, quantity);
            return;
        }

        log.info("更新 SKU {} 的库存，数量 {}（productId={}）", buyDto.getSkuId(), quantity, productId);
        // 事务内两级原子扣减（SKU 条件扣减 + 商品聚合），扣不动记 CRITICAL 供人工核对——
        // 这不是异常路径（重试同样扣不动）
        stockSyncService.deduct(buyDto);
    }

    private long nextRetryCount(String messageId) {
        try {
            String key = RETRY_KEY + messageId;
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, 24, TimeUnit.HOURS);
            }
            return count == null ? 1 : count;
        } catch (Exception ex) {
            log.error("记录库存消息重试次数失败，messageId={}", messageId, ex);
            return 1;
        }
    }

    /** 指数退避：1s/2s/4s */
    private void sleepBackoff(long retryCount) {
        try {
            Thread.sleep(Math.min(1000L * (1L << (retryCount - 1)), 8000));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void safeAck(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("确认库存消息异常", ex);
        }
    }

    private void safeNack(Channel channel, long deliveryTag, boolean requeue) {
        try {
            channel.basicNack(deliveryTag, false, requeue);
        } catch (Exception ex) {
            log.error("拒绝库存消息异常", ex);
        }
    }
}
