package com.itcast.model.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("rb_order")
public class Order {

    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 产品id
     */
    @TableField("product_id")
    private Integer productId;

    /**
     * 商品数量
     */
    private Integer quantity;

    /**
     * 优惠券id
     */
    @TableField("coupon_id")
    private Integer couponId;

    /**
     * 最终价格
     */
    @TableField("final_price")
    private BigDecimal finalPrice;

    /**
     * 订单归属人ID
     */
    @TableField("user_id")
    private Integer userId;

    /**
     * 状态
     */
    private Integer status;
}
