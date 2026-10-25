package com.itcast.mq.consumer;

import com.itcast.mapper.OrderDeadMessageMapper;
import com.itcast.model.pojo.OrderDeadMessage;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 死信落库组件：save.order / checkout.order 两个死信队列共用。
 * 只落库不自动补偿——失败点可能在扣库存/核券之前或之后，盲目补偿会超发，
 * 必须对照 product 侧流水人工核对后处理（rb_order_dead_message）。
 *
 * 入参用原始 AMQP Message 而非 Message&lt;T&gt;：死信 payload 是任意业务 DTO，
 * 声明具体泛型（如 Message&lt;String&gt;）会触发 Jackson 按声明类型反序列化，
 * JSON 对象读入 String 抛 MessageConversionException 导致死信被静默丢弃
 * （运行时探针验证过）。直接取 body 原文落库，保留可重放的原始 JSON。
 */
@Component
@Slf4j
public class DeadLetterRecorder {

    @Autowired
    private OrderDeadMessageMapper orderDeadMessageMapper;

    public void record(String source, org.springframework.amqp.core.Message message, Channel channel, long deliveryTag) {
        try {
            MessageProperties properties = message.getMessageProperties();
            OrderDeadMessage record = new OrderDeadMessage();
            record.setMessageId(properties != null ? properties.getMessageId() : null);
            record.setPayload(message.getBody() == null ? "" : new String(message.getBody(), StandardCharsets.UTF_8));
            record.setError(source + " retry exhausted");
            orderDeadMessageMapper.insert(record);
            log.error("{} 死信已落库，messageId={}", source, record.getMessageId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("{} 死信落库失败，payload={}", source,
                    message.getBody() == null ? "" : new String(message.getBody(), StandardCharsets.UTF_8), e);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception ex) {
                log.error("死信 nack 异常", ex);
            }
        }
    }
}

