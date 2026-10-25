package com.itcast.controller;

import com.itcast.model.vo.CouponVo;
import com.itcast.result.Result;
import com.itcast.service.CouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product")
public class CouponController {

    @Autowired
    private CouponService couponService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.itcast.config.InternalTokenGuard internalTokenGuard;

    @GetMapping("/getCouponsByUserId")
    public Result<List<CouponVo>> getCouponsByUserId() {
        return couponService.getCouponsByUserId();
    }

    @PostMapping("/useCoupon/{couponId}")
    public Result<Void> useCoupon(@PathVariable Long couponId) {
        return couponService.useCoupon(couponId);
    }

    /**
     * 内部接口：退还优惠券（order 服务订单超时取消时回滚核销）
     */
    @PostMapping("/helper/refundCoupon")
    public Result<Void> refundCoupon(@RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
                                     @RequestParam("userId") Integer userId,
                                     @RequestParam("couponId") Long couponId) {
        if (!internalTokenGuard.isValid(internalToken)) {
            return Result.failure("无效的内部令牌");
        }
        couponService.refundCoupon(couponId, userId);
        return Result.success(null);
    }
}
