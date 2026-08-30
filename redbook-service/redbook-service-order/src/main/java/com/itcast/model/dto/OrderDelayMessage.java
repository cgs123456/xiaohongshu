package com.itcast.model.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单延迟关单消息（落库后发往 order.delay.exchange，15分钟后由 order.close.queue 消费）
 */
@Data
public class OrderDelayMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 延迟交换机 */
    public static final String DELAY_EXCHANGE = "order.delay.exchange";
    /** 延迟队列路由键 */
    public static final String DELAY_ROUTING_KEY = "order.delay.key";

    /** 订单ID */
    private Long orderId;

    /** 商品ID */
    private Integer productId;

    /** 购买数量 */
    private Integer quantity;
}
