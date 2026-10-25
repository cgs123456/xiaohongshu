package com.itcast.mq.consumer;

import com.itcast.constant.MqConstant;
import com.itcast.mapper.ProductMapper;
import com.itcast.model.dto.BuyDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ProductConsumer {

    @Autowired
    private ProductMapper productMapper;

    @RabbitListener(queues = MqConstant.UPDATE_PRODUCT_STOCK_QUEUE)
    public void onMessage(BuyDto buyDto) {
        Integer productId = buyDto.getProductId();
        Integer quantity = buyDto.getQuantity() == null ? 1 : buyDto.getQuantity();

        // 补偿消息：MQ 发送失败时补发的回滚指令，直接回补 MySQL 库存
        if (Boolean.TRUE.equals(buyDto.getRestore())) {
            log.info("补偿回补商品库存，productId={} quantity={}", productId, quantity);
            executeWithRetry("回补库存", productId, () -> productMapper.restoreStock(productId, quantity));
            return;
        }

        log.info("更新商品{}的库存，数量{}", productId, quantity);
        // 原子条件扣减库存（stock >= quantity 才扣减），避免读改写竞态。
        // 扣不动说明 Redis/MySQL 库存已漂移：此时订单可能已创建（另一条消息），
        // 记 CRITICAL 供人工核对，不能盲目重试（会持续失败形成毒消息）
        int rows = executeWithRetry("扣减库存", productId, () -> productMapper.deductStock(productId, quantity));
        if (rows == 0) {
            log.error("CRITICAL: MySQL扣减库存失败（库存不足或商品不存在，Redis/MySQL 库存疑似漂移），" +
                    "productId={} quantity={}", productId, quantity);
        }
    }

    /**
     * DB 瞬时故障时原地重试一次（间隔 1 秒），再失败抛出交由默认重入队机制。
     * AUTO ack 下抛异常 Spring 会 requeue，无界循环风险由"只重试一次后放行给 MQ"控制。
     */
    private int executeWithRetry(String action, Integer productId, java.util.function.IntSupplier op) {
        try {
            return op.getAsInt();
        } catch (Exception first) {
            log.error("{}失败，1 秒后原地重试一次，productId={}", action, productId, first);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            return op.getAsInt();
        }
    }
}
