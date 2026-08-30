package com.itcast.task;

import com.itcast.mapper.OrderMapper;
import com.itcast.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 订单超时关单兜底任务：
 * 每60秒扫描一次创建超过15分钟仍待支付的订单（create_time DATETIME 列与 NOW()-INTERVAL 15 MINUTE 比较），
 * 执行与 OrderCloseConsumer 相同的关单逻辑，防止延迟消息丢失导致订单永远停留在待支付状态。
 */
@Component
@Slf4j
public class OrderTimeoutTask {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderService orderService;

    @Scheduled(fixedDelay = 60000)
    public void closeTimeoutOrders() {
        try {
            List<Long> timeoutOrderIds = orderMapper.selectTimeoutOrderIds();
            if (timeoutOrderIds == null || timeoutOrderIds.isEmpty()) {
                return;
            }
            log.info("扫描到超时待支付订单 {} 笔：{}", timeoutOrderIds.size(), timeoutOrderIds);
            for (Long orderId : timeoutOrderIds) {
                orderService.closeOrderIfDue(orderId);
            }
        } catch (Exception e) {
            log.error("超时订单扫描任务执行异常", e);
        }
    }
}
