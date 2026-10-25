package com.itcast.model.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单死信记录：save.order 死信队列消费落库，供人工/定时任务补偿。
 * 注意：不能自动回补库存——失败可能发生在扣库存消息之前或之后，
 * 盲目回补会超发；必须对照 product 侧流水人工核对后处理。
 */
@Data
@TableName("rb_order_dead_message")
public class OrderDeadMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("message_id")
    private String messageId;

    /** 消息体 JSON（BuyDto 序列化），补偿时人工核对用 */
    @TableField("payload")
    private String payload;

    @TableField("error")
    private String error;

    @TableField("create_time")
    private LocalDateTime createTime;
}
