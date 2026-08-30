package com.itcast.mq.consumer;

import com.itcast.constant.MqConstant;
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
    private static final String ORDER_RETRY_KEY = "order:retry:";

    @Autowired
    private OrderService orderService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @RabbitListener(queues = MqConstant.SAVE_ORDER_QUEUE)
    public void onMessage(Message<BuyDto> message, Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                          @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String amqpMessageId){
        BuyDto buyDto = message.getPayload();

        OrderDto orderDto = new OrderDto();
        orderDto.setProductId(buyDto.getProductId());
        orderDto.setSelectAttributes(buyDto.getSelectAttributes());
        orderDto.setUserId(buyDto.getUserId());
        orderDto.setQuantity(buyDto.getQuantity());

        // 使用发送方设置的UUID messageId做幂等（product服务通过MessagePostProcessor设置）；
        // 兜底：缺失时使用deliveryTag
        String messageId = amqpMessageId != null && !amqpMessageId.isBlank()
                ? amqpMessageId : String.valueOf(deliveryTag);
        orderDto.setMessageId(messageId);

        try {
            log.info("第一个消息消费者监听，处理消息：{}, messageId: {}", orderDto, messageId);

            // 处理订单保存（通过Service调用，事务会生效，包含幂等性检查）
            orderService.processOrderMessage(orderDto);

            // 消息确认
            channel.basicAck(deliveryTag, false);
            log.info("消息已确认，订单处理成功，messageId: {}", messageId);
        } catch (IllegalStateException e) {
            // 幂等性异常：消息已处理过或正在处理中，直接确认消息（不重新入队）
            log.warn("消息已处理过或正在处理中，跳过处理，messageId: {}, 错误: {}", messageId, e.getMessage());
            try {
                channel.basicAck(deliveryTag, false);
            } catch (Exception ex) {
                log.error("确认已处理消息时发生异常", ex);
            }
        } catch (Exception e) {
            // 失败重试计数：Redis INCR order:retry:{messageId}，TTL 24小时
            long retryCount = 0;
            try {
                String retryKey = ORDER_RETRY_KEY + messageId;
                Long count = redisTemplate.opsForValue().increment(retryKey);
                if (count != null && count == 1) {
                    redisTemplate.expire(retryKey, 24, TimeUnit.HOURS);
                }
                retryCount = count == null ? 0 : count;
            } catch (Exception ex) {
                log.error("记录消息重试次数失败，messageId: {}", messageId, ex);
            }

            if (retryCount > MAX_RETRY_COUNT) {
                log.error("消息处理失败且重试次数已达上限（{}次），进入死信队列，messageId: {}",
                        MAX_RETRY_COUNT, messageId, e);
                try {
                    channel.basicNack(deliveryTag, false, false);
                } catch (Exception ex) {
                    log.error("拒绝消息时发生异常", ex);
                }
            } else {
                log.error("处理订单消息时发生异常，消息将重新入队，messageId: {}，当前重试次数: {}",
                        messageId, retryCount, e);
                try {
                    channel.basicNack(deliveryTag, false, true);
                } catch (Exception ex) {
                    log.error("拒绝消息时发生异常", ex);
                }
            }
        }
    }
}
