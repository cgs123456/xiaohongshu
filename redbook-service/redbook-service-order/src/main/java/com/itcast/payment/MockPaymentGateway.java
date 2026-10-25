package com.itcast.payment;

import com.itcast.model.pojo.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 模拟支付网关（默认）：同步立即完成，无跳转、无回调。
 * 接入真实网关时新增实现类并以 payment.gateway=<channel> 切换，
 * 本实现自动退位（ConditionalOnProperty matchIfMissing）。
 *
 * 渠道侧状态用 Redis 标记 mock:pay:paid:{tradeNo} 模拟：回调到达即置位，
 * queryPayment 读标记还原"渠道已支付但回调丢失"的可测场景
 * （手工置标记 = 用户在渠道完成支付而回调未送达）。
 */
@Component
@ConditionalOnProperty(name = "payment.gateway", havingValue = "mock", matchIfMissing = true)
@Slf4j
public class MockPaymentGateway implements PaymentGateway {

    private static final String PAID_FLAG_PREFIX = "mock:pay:paid:";
    /** 渠道侧标记保留时长（对账窗口） */
    private static final long PAID_FLAG_TTL_HOURS = 24;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public PayCreateResult createPayment(Order order) {
        String tradeNo = "MOCK-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        log.info("模拟支付成功，orderId={} tradeNo={} amount={}", order.getId(), tradeNo, order.getFinalPrice());
        return PayCreateResult.done(tradeNo);
    }

    @Override
    public PayNotifyResult parseNotify(Map<String, String> params) {
        // 模拟网关无异步回调；真实渠道接入后由各自实现验签解析
        return new PayNotifyResult(false, null, null, "模拟网关无回调");
    }

    @Override
    public PayQueryStatus queryPayment(String tradeNo) {
        Boolean paid = redisTemplate.hasKey(PAID_FLAG_PREFIX + tradeNo);
        return Boolean.TRUE.equals(paid) ? PayQueryStatus.PAID : PayQueryStatus.NOT_FOUND;
    }

    @Override
    public String channel() {
        return "MOCK";
    }

    /** 渠道侧置"已支付"标记（回调路径调用；测试可手工置位模拟回调丢失） */
    public void markChannelPaid(String tradeNo) {
        redisTemplate.opsForValue().set(PAID_FLAG_PREFIX + tradeNo, "1", PAID_FLAG_TTL_HOURS, TimeUnit.HOURS);
    }
}
