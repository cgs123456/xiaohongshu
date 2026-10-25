package com.itcast.service;

import com.itcast.model.dto.CheckoutDto;
import com.itcast.result.Result;

public interface CheckoutService {

    /**
     * 购物车结算下单（多商品一单，含优惠券核销与收货信息）
     */
    Result<String> checkout(CheckoutDto dto);
}
