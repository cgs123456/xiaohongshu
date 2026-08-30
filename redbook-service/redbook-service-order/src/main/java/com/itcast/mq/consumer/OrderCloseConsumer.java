package com.itcast.mq.consumer;

import com.itcast.config.OrderDelayConfig;
import com.itcast.model.dto.OrderDelayMessage;
import com.itcast.service.OrderService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 订单超时关单消费者：
 * 消费 order.close.queue（由 order.delay.queue 中 TTL 15分钟的消息死信转发而来），
 * 若订单仍处于待支付状态则取消订单并回补库存。
 */
@Component
@Slf4j
public class OrderCloseConsumer {

    @Autowired
    private OrderService orderService;

    @RabbitListener(queues = OrderDelayConfig.CLOSE_QUEUE)
    public void onCloseMessage(OrderDelayMessage delayMessage, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("收到订单超时关单消息：{}", delayMessage);
        try {
            orderService.closeOrderIfDue(delayMessage.getOrderId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("订单超时关单处理失败，message: {}", delayMessage, e);
            try {
                // 关单失败不重新入队，避免反复失败；超时订单由定时兜底任务再扫描
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception ex) {
                log.error("拒绝关单消息时发生异常", ex);
            }
        }
    }
}
