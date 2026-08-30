package com.itcast.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itcast.model.pojo.Coupon;
import com.itcast.model.pojo.UserCoupon;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {
    List<Coupon> getCouponsByUserId(Integer userId);

    /**
     * 核销用户优惠券（仅未使用的会被更新，影响行数为0表示未持有或已使用）
     */
    @Update("UPDATE rb_user_coupon SET status = 1 WHERE user_id = #{userId} AND coupon_id = #{couponId} AND status = 0")
    int markUsed(@Param("userId") Integer userId, @Param("couponId") Integer couponId);
}
