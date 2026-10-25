package com.itcast.model.vo;

import com.itcast.model.pojo.Order;
import com.itcast.model.pojo.OrderItem;
import lombok.Data;

import java.util.List;

/**
 * 订单视图：主表 + 明细
 */
@Data
public class OrderVo {

    private Order order;

    private List<OrderItem> items;
}
