package com.itcast.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itcast.model.pojo.Coupon;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CouponMapper extends BaseMapper<Coupon> {
}
