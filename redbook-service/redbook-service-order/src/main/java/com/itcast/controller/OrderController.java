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
     * 发起支付：同步渠道（模拟）立即完成（data=null）；跳转渠道返回支付 URL（data=payUrl）
     */
    @PostMapping("/pay/{orderId}")
    public Result<String> payOrder(@PathVariable String orderId) {
        Long id;
        try {
            id = Long.valueOf(orderId);
        } catch (NumberFormatException e) {
            return Result.failure("订单ID格式错误");
        }
        // 属主校验在 service 内完成（只允许支付自己的订单）
        return orderService.payOrder(id, UserContext.getUserId());
    }

    /**
     * 支付网关异步回调（免登录——外部网关服务器调用，安全靠渠道验签）。
     * 应答渠道约定的纯文本，不包 Result。
     */
    @PostMapping(value = "/pay/notify", produces = org.springframework.http.MediaType.TEXT_PLAIN_VALUE)
    public String payNotify(@RequestParam java.util.Map<String, String> params) {
        boolean ok = orderService.handlePayNotify(params);
        return ok ? "success" : "fail";
    }
}
