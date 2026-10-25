package com.itcast.service;

import com.itcast.model.pojo.Coupon;
import com.itcast.model.vo.CouponVo;
import com.itcast.result.Result;

import java.util.List;

public interface CouponService {
    Result<List<CouponVo>> getCouponsByUserId();

    /** 当前登录用户视角的可用优惠券 */
    List<CouponVo> getCouponsByUserId(Integer userId);

    Result<Void> useCoupon(Long couponId);

    /**
     * 核销优惠券（指定用户）：markUsed + deductStock 必须在同一事务内，
     * 库存扣减失败时核销标记一并回滚
     */
    void useCoupon(Long couponId, Integer userId);

    /**
     * 退还优惠券（结算链路发送 MQ 失败补偿 / 订单超时取消退款）：
     * 撤销核销标记并回补券库存，同一事务
     */
    void refundCoupon(Long couponId, Integer userId);

    Result<Void> insertCoupon(Coupon coupon);

}
