package com.itcast.controller;

import com.itcast.context.UserContext;
import com.itcast.model.vo.OrderVo;
import com.itcast.result.Result;
import com.itcast.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/getOrderList")
    public Result<List<OrderVo>> getOrderList(@RequestParam(required = false) Integer page,
                                              @RequestParam(required = false) Integer pageSize) {
        Integer userId = UserContext.getUserId();
        return orderService.getOrderListByUserId(userId, page, pageSize);
    }

    /**
     * 模拟支付：把待支付订单置为已支付并落支付流水
     */
    @PostMapping("/pay/{orderId}")
    public Result<Void> payOrder(@PathVariable String orderId) {
        Long id;
        try {
            id = Long.valueOf(orderId);
        } catch (NumberFormatException e) {
            return Result.failure("订单ID格式错误");
        }
        // 属主校验在 service 内完成（只允许支付自己的订单）
        return orderService.payOrder(id, UserContext.getUserId());
    }
}
