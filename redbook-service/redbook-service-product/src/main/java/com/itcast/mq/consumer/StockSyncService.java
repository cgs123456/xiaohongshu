package com.itcast.mq.consumer;

import com.itcast.mapper.SkuMapper;
import com.itcast.model.dto.BuyDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存同步执行器：SKU 扣减/回补与商品聚合库存两级写必须在同一事务，
 * 独立成 Bean 保证 @Transactional 经代理生效（消费者直接调用事务方法会因 this 调用失效）。
 */
@Service
@Slf4j
public class StockSyncService {

    @Autowired
    private SkuMapper skuMapper;

    /** MySQL 两级原子扣减（SKU 条件扣减 + 聚合同步扣减），SKU 层扣不动抛给上层记 CRITICAL */
    @Transactional(rollbackFor = Exception.class)
    public void deduct(BuyDto buyDto) {
        Integer skuId = buyDto.getSkuId();
        if (skuId == null) {
            throw new IllegalArgumentException("库存消息缺少 skuId");
        }
        int quantity = buyDto.getQuantity() == null ? 1 : buyDto.getQuantity();
        int rows = skuMapper.deductStock(skuId, quantity);
        if (rows == 0) {
            log.error("CRITICAL: MySQL SKU 扣减库存失败（库存不足或 SKU 不存在，Redis/MySQL 疑似漂移），" +
                    "skuId={} quantity={}", skuId, quantity);
            return;
        }
        int aggregateRows = skuMapper.deductProductStock(buyDto.getProductId(), quantity);
        if (aggregateRows == 0) {
            // SKU 已扣成功而聚合列扣不动：聚合展示口径漂移，留日志线索（不影响 SKU 事实库存）
            log.warn("商品聚合库存扣减未命中（疑似与 SKU 之和漂移），productId={} quantity={}",
                    buyDto.getProductId(), quantity);
        }
    }

    /** MySQL 两级回补（补偿/关单回滚） */
    @Transactional(rollbackFor = Exception.class)
    public void restore(Integer skuId, Integer productId, int quantity) {
        if (skuId == null) {
            throw new IllegalArgumentException("回补消息缺少 skuId");
        }
        skuMapper.restoreStock(skuId, quantity);
        if (productId != null) {
            skuMapper.restoreProductStock(productId, quantity);
        }
    }
}
