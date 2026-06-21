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

    @GetMapping("/getCouponsByUserId")
    public Result<List<CouponVo>> getCouponsByUserId() {
        return couponService.getCouponsByUserId();
    }

    @GetMapping("/useCoupon/{couponId}")
    public Result<Void> useCoupon(@PathVariable Long couponId) {
        return couponService.useCoupon(couponId);
    }

    @GetMapping("/helper/getCouponsByUserId")
    public List<CouponVo> getCouponsByUserIdHelper() {
        return couponService.getCouponsByUserId().getData();
    }

    @GetMapping("/helper/useCoupon")
    public Void useCouponHelper(@RequestParam Long couponId) {
        return couponService.useCoupon(couponId).getData();
    }
}
