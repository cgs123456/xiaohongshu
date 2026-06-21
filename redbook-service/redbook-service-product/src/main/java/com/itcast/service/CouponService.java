package com.itcast.service;

import com.itcast.model.pojo.Coupon;
import com.itcast.model.vo.CouponVo;
import com.itcast.result.Result;

import java.util.List;

public interface CouponService {
    Result<List<CouponVo>> getCouponsByUserId();

    Result<Void> useCoupon(Long couponId);

    Result<Void> insertCoupon(Coupon coupon);

}
