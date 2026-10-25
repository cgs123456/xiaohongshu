package com.itcast.model.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水：模拟支付落账，供对账与退款依据
 */
@Data
@TableName("rb_payment")
public class Payment {

    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("order_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    @TableField("user_id")
    private Integer userId;

    private BigDecimal amount;

    @TableField("pay_method")
    private String payMethod;

    /** 1-成功 0-失败 */
    private Integer status;

    @TableField("pay_time")
    private LocalDateTime payTime;
}
