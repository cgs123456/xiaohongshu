package com.itcast.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itcast.model.pojo.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 查询创建超过15分钟仍待支付（status=0）的订单ID（使用表中 create_time DATETIME 列）
     */
    @Select("SELECT id FROM rb_order WHERE status = 0 AND create_time < NOW() - INTERVAL 15 MINUTE")
    List<Long> selectTimeoutOrderIds();
}
