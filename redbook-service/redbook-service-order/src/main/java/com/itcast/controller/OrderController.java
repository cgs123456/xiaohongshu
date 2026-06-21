package com.itcast.controller;

import com.itcast.context.UserContext;
import com.itcast.model.dto.OrderDto;
import com.itcast.model.pojo.Order;
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

    @PostMapping("/saveOrder")
    public Result<Void> saveOrder(@RequestBody OrderDto orderDto) {
        return orderService.saveOrder(orderDto);
    }

    @GetMapping("/getOrderList")
    public Result<List<Order>> getOrderList() {
        Integer userId = UserContext.getUserId();
        return orderService.getOrderListByUserId(userId);
    }
}
