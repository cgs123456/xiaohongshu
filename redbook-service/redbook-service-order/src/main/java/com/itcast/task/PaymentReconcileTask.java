package com.itcast.task;

import com.itcast.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 支付查单补偿调度：仅触发，补偿逻辑在 OrderService（支付域内聚）。
 * 跳转类渠道回调丢失时由 reconcilePendingPayments 主动向渠道核实，
 * 已支付补完成订单、未支付超时关单。模拟网关无跳转支付，任务空转。
 */
@Component
@Slf4j
public class PaymentReconcileTask {

    @Autowired
    private OrderService orderService;

    @Scheduled(fixedDelay = 60000)
    public void reconcile() {
        try {
            orderService.reconcilePendingPayments();
        } catch (Exception e) {
            log.error("支付查单补偿任务执行异常", e);
        }
    }
}
