package com.itcast.model.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 购物车结算下单消息（product → order，Jackson 序列化）。
 * 下沉 redbook-common 单一副本（此前 product/order 双副本需人工保持同步）。
 * 包名保持 com.itcast.model.dto 不变，__TypeId__ 全限定名兼容在途消息。
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
