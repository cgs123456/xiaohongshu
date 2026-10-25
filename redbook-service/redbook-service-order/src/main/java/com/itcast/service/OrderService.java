package com.itcast.service;

import com.itcast.model.dto.CheckoutOrderDto;
import com.itcast.model.dto.OrderDto;
import com.itcast.model.pojo.Order;
import com.itcast.model.vo.OrderVo;
import com.itcast.result.Result;

import java.util.List;

public interface OrderService {

    /**
     * 处理消息队列中的订单保存（带事务）
     * @param orderDto 订单DTO
     */
    void processOrderMessage(OrderDto orderDto);

    /**
     * 处理购物车结算消息：创建订单主表（快照/地址/金额拆解）+ 明细（带事务）
     */
    void processCheckoutMessage(String messageId, CheckoutOrderDto dto);

    /**
     * 根据用户ID获取订单列表（含明细；page/pageSize 缺省时默认 1/20）
     */
    Result<List<OrderVo>> getOrderListByUserId(Integer userId, Integer page, Integer pageSize);

    /**
     * 模拟支付：校验属主与待支付状态后置为已支付并落支付流水
     */
    /** 发起支付：data 为 null=同步完成，非 null=支付跳转 URL（等渠道异步回调完成） */
    Result<String> payOrder(Long orderId, Integer userId);

    /** 支付网关异步回调处理（验签+完成支付），成功返回 true */
    boolean handlePayNotify(java.util.Map<String, String> params);

    /**
     * 关单公共逻辑：若订单仍处于待支付状态则取消，回补商品库存并退还优惠券
     * @param orderId 订单ID
     */
    void closeOrderIfDue(Long orderId);

    /**
     * 库存回补重试：从 Redis 重试队列弹出一条任务执行一次。
     * @return 队列为空或重试成功返回 true；本次失败（已重新入队）返回 false
     */
    boolean retryOneStockRestore();
}
