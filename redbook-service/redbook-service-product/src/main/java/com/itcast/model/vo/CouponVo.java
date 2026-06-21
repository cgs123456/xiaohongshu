package com.itcast.model.vo;

import com.itcast.model.pojo.Coupon;
import lombok.Data;

@Data
public class CouponVo {

    private Coupon coupon;

    /**
     * 是否可用
     */
    private Boolean isUsable;
}
