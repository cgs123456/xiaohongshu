package com.itcast.model.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细：商品名称/图片/单价为下单时快照，商品后续改价不影响历史订单
 */
@Data
@TableName("rb_order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @TableField("order_id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long orderId;

    @TableField("product_id")
    private Integer productId;
    /** 规格id（SKU 库存扣减口径；历史订单为 null，回补走 productId 兜底） */
    private Integer skuId;

    @TableField("product_name")
    private String productName;

    @TableField("product_image")
    private String productImage;

    @TableField("unit_price")
    private BigDecimal unitPrice;

    private Integer quantity;

    /** 商品属性快照（"label:value; label:value"，仅单品立即购买路径携带） */
    private String attributes;
}
