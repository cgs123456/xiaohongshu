package com.itcast.mq.consumer;

import com.google.gson.Gson;
import com.itcast.constant.MqConstant;
import com.itcast.exception.DuplicateMessageException;
import com.itcast.model.dto.BuyDto;
import com.itcast.model.dto.OrderDto;
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

@Component
@Slf4j
public class OrderConsumer {

    /** 单条消息最大重试次数，超过后进死信队列 */
    private static final int MAX_RETRY_COUNT = 3;

    /** 消息重试计数 key 前缀 */
    private static final String RETRY_KEY = "order:retry:";

    private final Gson gson = new Gson();

    @Autowired
    private OrderService orderService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @RabbitListener(queues = MqConstant.SAVE_ORDER_QUEUE)
    public void onMessage(Message<BuyDto> message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                          @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String amqpMessageId) {
        BuyDto buyDto = message.getPayload();

        // 生产者（product 服务）必须为每条消息设置 UUID messageId；
        // 不能用 deliveryTag 兜底——消费端重启后 deliveryTag 从 1 重新计数，
        // 会把不同消息误判为已处理而丢弃。缺失即协议违规，直接进死信。
        if (amqpMessageId == null || amqpMessageId.isBlank()) {
            log.error("消息缺少 messageId，视为毒消息转入死信队列，payload={}",
                    gson.toJson(buyDto));
            com.itcast.mq.MqConsumerSupport.safeNack(channel, deliveryTag, false, "订单消息");
            return;
        }
        String messageId = amqpMessageId;

        OrderDto orderDto = new OrderDto();
        orderDto.setProductId(buyDto.getProductId());
        orderDto.setSelectAttributes(buyDto.getSelectAttributes());
        orderDto.setUserId(buyDto.getUserId());
        orderDto.setQuantity(buyDto.getQuantity());
        orderDto.setMessageId(messageId);

        try {
            log.info("订单消息消费者处理消息：{}, messageId: {}", orderDto, messageId);

            // 处理订单保存（通过Service调用，事务会生效，包含幂等性检查）
            orderService.processOrderMessage(orderDto);

            // 消息确认
            channel.basicAck(deliveryTag, false);
            log.info("消息已确认，订单处理成功，messageId: {}", messageId);
        } catch (DuplicateMessageException e) {
            // 幂等重复：消息已处理过或正在处理中，直接确认丢弃。
            // 只有这一种异常可以静默 ACK——业务失败走下面的重试/死信路径
            log.warn("消息已处理过或正在处理中，跳过处理，messageId: {}, 错误: {}", messageId, e.getMessage());
            com.itcast.mq.MqConsumerSupport.safeAck(channel, deliveryTag, "订单消息");
        } catch (IllegalArgumentException e) {
            // 毒消息（参数缺失/非法）：重试永远不会成功，直接进死信
            log.error("订单消息参数非法，转入死信队列，messageId: {}", messageId, e);
            redisTemplate.delete(RETRY_KEY + messageId);
            com.itcast.mq.MqConsumerSupport.safeNack(channel, deliveryTag, false, "订单消息");
        } catch (Exception e) {
            // 瞬时故障（DB/Feign 抖动等）：指数退避后重新入队，超过上限进死信
            long retryCount = com.itcast.mq.MqConsumerSupport.nextRetryCount(redisTemplate, RETRY_KEY, messageId);
            if (retryCount > MAX_RETRY_COUNT) {
                log.error("消息处理失败且重试次数已达上限（{}次），进入死信队列，messageId: {}",
                        MAX_RETRY_COUNT, messageId, e);
                redisTemplate.delete(RETRY_KEY + messageId);
                com.itcast.mq.MqConsumerSupport.safeNack(channel, deliveryTag, false, "订单消息");
            } else {
                log.error("处理订单消息异常，退避后重新入队，messageId: {}，第 {} 次重试",
                        messageId, retryCount, e);
                com.itcast.mq.MqConsumerSupport.sleepBackoff(retryCount);
                com.itcast.mq.MqConsumerSupport.safeNack(channel, deliveryTag, true, "订单消息");
            }
        }
    }

    /**
     * 死信消费者：落库到 rb_order_dead_message 供人工核对（见 DeadLetterRecorder，
     * 不自动补偿——失败点可能在扣库存之前或之后，盲目回补会超发）。
     * 参数用原始 AMQP Message，避免 payload 反序列化失败丢死信
     */
    @RabbitListener(queues = "save.order.dead.queue")
    public void onDeadLetter(org.springframework.amqp.core.Message message, Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        deadLetterRecorder.record("save.order", message, channel, deliveryTag);
    }

    @Autowired
    private DeadLetterRecorder deadLetterRecorder;





}
