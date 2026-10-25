package com.itcast.payment;

import com.itcast.model.pojo.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * 模拟支付网关（默认）：同步立即完成，无跳转、无回调。
 * 接入真实网关时新增实现类并以 payment.gateway=<channel> 切换，
 * 本实现自动退位（ConditionalOnProperty matchIfMissing）。
 */
@Component
@ConditionalOnProperty(name = "payment.gateway", havingValue = "mock", matchIfMissing = true)
@Slf4j
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PayCreateResult createPayment(Order order) {
        String tradeNo = "MOCK-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        log.info("模拟支付成功，orderId={} tradeNo={} amount={}", order.getId(), tradeNo, order.getFinalPrice());
        return PayCreateResult.done(tradeNo);
    }

    @Override
    public PayNotifyResult parseNotify(Map<String, String> params) {
        // 模拟网关无异步回调
        return new PayNotifyResult(false, null, null, "模拟网关无回调");
    }

    @Override
    public String channel() {
        return "MOCK";
    }
}
