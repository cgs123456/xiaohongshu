package com.itcast.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itcast.model.pojo.OrderDeadMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderDeadMessageMapper extends BaseMapper<OrderDeadMessage> {
}
