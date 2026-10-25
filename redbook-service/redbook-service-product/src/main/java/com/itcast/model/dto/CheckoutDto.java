package com.itcast.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 购物车结算请求
 */
@Data
public class CheckoutDto {

    /** 收货人 */
    private String receiverName;

    /** 收货手机号 */
    private String receiverPhone;

    /** 收货地址 */
    private String address;

    /** 使用的优惠券 id（可空） */
    private Long couponId;

    /** 只结算指定规格（购物车项的 skuId 集合，同商品不同规格独立行）；为空表示结算全部购物车 */
    private List<Integer> skuIds;

    /**
     * 商品属性选择（label -> 选中值），仅"单品立即购买"路径使用（BuyModal 单商品结算）；
     * 购物车结算（多商品）不传。随单快照落 rb_order_item.attributes，不做 SKU 级库存区分
     */
    private java.util.Map<String, String> attributes;
}
