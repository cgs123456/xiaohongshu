package com.itcast.service;

import com.itcast.model.dto.OrderDto;
import com.itcast.model.pojo.Order;
import com.itcast.result.Result;

import java.util.List;

public interface OrderService {

    /**
     * 处理消息队列中的订单保存（带事务）
     * @param orderDto 订单DTO
     */
    void processOrderMessage(OrderDto orderDto);

    /**
     * 根据用户ID获取订单列表（page/pageSize 为空时返回全部，兼容现状）
     * @param userId 用户ID
     * @return 订单列表
     */
    Result<List<Order>> getOrderListByUserId(Integer userId, Integer page, Integer pageSize);

    /**
     * 关单公共逻辑：若订单仍处于待支付状态则取消，并回补商品库存
     * @param orderId 订单ID
     */
    void closeOrderIfDue(Long orderId);
}
