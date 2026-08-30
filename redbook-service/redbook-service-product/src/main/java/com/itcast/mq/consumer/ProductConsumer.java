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
    public void onMessage(BuyDto buyDto){
        Integer productId = buyDto.getProductId();
        Integer quantity = buyDto.getQuantity() == null ? 1 : buyDto.getQuantity();
        log.info("更新商品{}的库存，数量{}", productId, quantity);
        // 原子条件扣减库存（stock >= quantity 才扣减），避免读改写竞态
        int rows = productMapper.deductStock(productId, quantity);
        if (rows == 0) {
            log.error("MySQL扣减库存失败（库存不足或商品不存在），productId={} quantity={}", productId, quantity);
        }
    }

}
