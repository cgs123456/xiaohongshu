package com.itcast.mq.consumer;

import com.itcast.constant.MqConstant;
import com.itcast.exception.DuplicateMessageException;
import com.itcast.model.dto.CheckoutOrderDto;
import com.itcast.service.OrderService;
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
 * 购物车结算消息消费者：与 OrderConsumer 相同的异常分类/退避/死信策略。
 * DuplicateMessageException → ACK；参数毒消息 → 直接死信；其它异常 → 指数退避重试后死信。
 */
@Component
@Slf4j
public class CheckoutConsumer {

    private static final int MAX_RETRY_COUNT = 3;
    private static final String RETRY_KEY = "checkout:retry:";

    @Autowired
    private OrderService orderService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private DeadLetterRecorder deadLetterRecorder;

    @RabbitListener(queues = MqConstant.CHECKOUT_ORDER_QUEUE)
    public void onMessage(Message<CheckoutOrderDto> message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                          @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        CheckoutOrderDto payload = message.getPayload();
        if (messageId == null || messageId.isBlank()) {
            // 缺失 messageId 无法做幂等（deliveryTag 重启后重数会误判），视为毒消息
            log.error("结算消息缺少 messageId，转入死信，payload={}", payload);
            safeNack(channel, deliveryTag, false);
            return;
        }

        try {
            orderService.processCheckoutMessage(messageId, payload);
            channel.basicAck(deliveryTag, false);
            log.info("结算消息处理成功，messageId={}", messageId);
        } catch (DuplicateMessageException e) {
            log.warn("结算消息重复，跳过，messageId={}", messageId);
            safeAck(channel, deliveryTag);
        } catch (IllegalArgumentException e) {
            log.error("结算消息参数非法，转入死信，messageId={}", messageId, e);
            redisTemplate.delete(RETRY_KEY + messageId);
            safeNack(channel, deliveryTag, false);
        } catch (Exception e) {
            long retryCount = nextRetryCount(messageId);
            if (retryCount > MAX_RETRY_COUNT) {
                log.error("结算消息重试超限进死信，messageId={}", messageId, e);
                redisTemplate.delete(RETRY_KEY + messageId);
                safeNack(channel, deliveryTag, false);
            } else {
                log.error("结算消息处理异常，退避重试，messageId={} 第 {} 次", messageId, retryCount, e);
                sleepBackoff(retryCount);
                safeNack(channel, deliveryTag, true);
            }
        }
    }

    /** 结算死信落库（与 save.order 死信同表）。参数用原始 AMQP Message，避免 payload 反序列化失败丢死信（见 DeadLetterRecorder） */
    @RabbitListener(queues = "checkout.order.dead.queue")
    public void onDeadLetter(org.springframework.amqp.core.Message message, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        deadLetterRecorder.record("checkout", message, channel, deliveryTag);
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
            log.error("记录结算重试次数失败，messageId={}", messageId, ex);
            return 1;
        }
    }

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
            log.error("确认消息异常", ex);
        }
    }

    private void safeNack(Channel channel, long deliveryTag, boolean requeue) {
        try {
            channel.basicNack(deliveryTag, false, requeue);
        } catch (Exception ex) {
            log.error("拒绝消息异常", ex);
        }
    }
}
