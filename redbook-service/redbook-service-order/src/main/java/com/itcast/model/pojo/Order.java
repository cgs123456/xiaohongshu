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

@Data
@TableName("rb_order")
public class Order {

    /**
     * 主键（雪花 id；序列化为字符串防止前端 JS Number 精度丢失）
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 订单归属人ID
     */
    @TableField("user_id")
    private Integer userId;

    /**
     * 状态：0-待支付 1-已支付 2-已发货 3-已完成 4-已取消
     */
    private Integer status;

    /**
     * 商品总价（下单时快照）
     */
    @TableField("total_price")
    private BigDecimal totalPrice;

    /**
     * 优惠金额
     */
    @TableField("discount_price")
    private BigDecimal discountPrice;

    /**
     * 实付金额 = 总价 - 优惠
     */
    @TableField("final_price")
    private BigDecimal finalPrice;

    /**
     * 使用的优惠券id（与 rb_coupon 主键同为 BIGINT）
     */
    @TableField("coupon_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long couponId;

    /**
     * 收货人姓名
     */
    @TableField("receiver_name")
    private String receiverName;

    /**
     * 收货人手机号
     */
    @TableField("receiver_phone")
    private String receiverPhone;

    /**
     * 收货地址
     */
    private String address;

    /**
     * 支付时间
     */
    @TableField("pay_time")
    private LocalDateTime payTime;

    /**
     * 下单时间（DB DEFAULT CURRENT_TIMESTAMP 填充，查询时映射）
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * （旧单字段）产品id——立即购买老链路遗留，新结算链路用 rb_order_item
     */
    @TableField("product_id")
    private Integer productId;

    /**
     * （旧单字段）商品数量
     */
    private Integer quantity;
}
