package com.itcast.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车结算下单消息（product → order，RabbitMQ JDK 序列化）。
 * 注意：order 服务持有本类的完全相同副本（字段与 serialVersionUID 必须一致），
 * 任一侧改字段需同步另一侧（历史遗留的 JDK 序列化约定，统一 JSON 序列化前维持现状）。
 */
@Data
public class CheckoutOrderDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer userId;

    private String receiverName;
    private String receiverPhone;
    private String address;

    /** 使用的优惠券（可空） */
    private Long couponId;

    /** 优惠金额（0 表示未用券） */
    private BigDecimal discount;

    /** 结算商品明细（含下单时快照） */
    private List<CheckoutItem> items;

    @Data
    public static class CheckoutItem implements Serializable {
        private static final long serialVersionUID = 1L;

        private Integer productId;
        /** 规格id（库存扣减口径；无属性商品为默认 SKU） */
        private Integer skuId;
        private String productName;
        private String productImage;
        private BigDecimal unitPrice;
        private Integer quantity;
        /** 商品属性快照（"label:value; label:value"，仅单品立即购买路径携带） */
        private String attributes;
    }
}
