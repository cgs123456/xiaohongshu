package com.itcast.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itcast.model.pojo.Coupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {

    /**
     * 原子扣减优惠券库存（影响行数为0表示库存不足）
     */
    @Update("UPDATE rb_coupon SET stock = stock - 1 WHERE id = #{couponId} AND stock > 0")
    int deductStock(@Param("couponId") Long couponId);
}
