package com.itcast.controller;

import com.itcast.context.UserContext;
import com.itcast.model.pojo.Order;
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
    public Result<List<Order>> getOrderList(@RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer pageSize) {
        Integer userId = UserContext.getUserId();
        return orderService.getOrderListByUserId(userId, page, pageSize);
    }
}
