package com.itcast.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单超时关单配置：
 * 下单后向 order.delay.exchange 发送消息，进入 order.delay.queue（TTL 15分钟），
 * 到期后经死信路由转发到 order.close.exchange，由 order.close.queue 消费执行关单。
 */
@Configuration
public class OrderDelayConfig {

    /** 延迟交换机 */
    public static final String DELAY_EXCHANGE = "order.delay.exchange";
    /** 延迟队列（x-message-ttl=900000 即15分钟） */
    public static final String DELAY_QUEUE = "order.delay.queue";
    /** 延迟队列路由键 */
    public static final String DELAY_ROUTING_KEY = "order.delay.key";
    /** 关单交换机（延迟队列的死信交换机） */
    public static final String CLOSE_EXCHANGE = "order.close.exchange";
    /** 关单队列 */
    public static final String CLOSE_QUEUE = "order.close.queue";
    /** 关单路由键（延迟队列的死信路由键） */
    public static final String CLOSE_ROUTING_KEY = "order.close.key";

    @Bean
    public DirectExchange orderDelayExchange() {
        return new DirectExchange(DELAY_EXCHANGE);
    }

    @Bean
    public Queue orderDelayQueue() {
        Map<String, Object> args = new HashMap<>();
        // 消息在队列中保留15分钟
        args.put("x-message-ttl", 900000);
        // 到期后转发到关单交换机
        args.put("x-dead-letter-exchange", CLOSE_EXCHANGE);
        args.put("x-dead-letter-routing-key", CLOSE_ROUTING_KEY);
        return new Queue(DELAY_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue()).to(orderDelayExchange()).with(DELAY_ROUTING_KEY);
    }

    @Bean
    public DirectExchange orderCloseExchange() {
        return new DirectExchange(CLOSE_EXCHANGE);
    }

    @Bean
    public Queue orderCloseQueue() {
        return QueueBuilder.durable(CLOSE_QUEUE).build();
    }

    @Bean
    public Binding orderCloseBinding() {
        return BindingBuilder.bind(orderCloseQueue()).to(orderCloseExchange()).with(CLOSE_ROUTING_KEY);
    }
}
