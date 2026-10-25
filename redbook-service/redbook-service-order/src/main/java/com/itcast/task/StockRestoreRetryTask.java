package com.itcast.task;

import com.itcast.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 库存回补重试任务：消费 order:stock:restore:retry 队列中回补失败的记录。
 * 场景：订单超时关单已把状态改为 CANCEL，但 Feign 回补 product 库存失败——
 * 此时兜底扫描（status=0）不会再命中该订单，不重试库存就永久少一份。
 * 失败的任务会重新入队，下个周期继续；单次最多处理 20 条，失败即提前退出。
 */
@Component
@Slf4j
public class StockRestoreRetryTask {

    private static final int MAX_BATCH = 20;

    @Autowired
    private OrderService orderService;

    @Scheduled(fixedDelay = 120000)
    public void retryStockRestores() {
        try {
            for (int i = 0; i < MAX_BATCH; i++) {
                if (!orderService.retryOneStockRestore()) {
                    log.warn("库存回补重试失败，提前结束本批，下个周期继续");
                    break;
                }
            }
        } catch (Exception e) {
            log.error("库存回补重试任务执行异常", e);
        }
    }
}
