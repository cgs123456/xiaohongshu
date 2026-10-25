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

    /** 只结算指定商品（购物车项的 productId 集合）；为空表示结算全部购物车 */
    private List<Integer> productIds;
}
