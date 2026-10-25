package com.itcast.payment;

import com.itcast.model.pojo.Order;

import java.util.Map;

/**
 * 支付网关抽象：接入真实网关（支付宝/微信等）时实现本接口并注册为 Bean 即可，
 * 业务代码（下单/支付/回调）不感知具体渠道。
 *
 * 生命周期约定：
 * 1. createPayment：用户点击支付时调用。同步即可完成的渠道（余额/模拟）返回 SUCCESS；
 *    跳转类渠道返回 PENDING + payUrl（前端跳转），等待异步回调。
 * 2. parseNotify：网关异步回调入口 /order/pay/notify 调用，负责验签与解析，
 *    返回订单号与是否支付成功。业务侧据此完成订单状态机流转。
 */
public interface PaymentGateway {

    /** 发起支付 */
    PayCreateResult createPayment(Order order);

    /** 解析并校验异步回调（验签失败或非支付成功回调返回 failed=true） */
    PayNotifyResult parseNotify(Map<String, String> params);

    /** 渠道标识（落 rb_payment.pay_method） */
    String channel();

    /** 支付发起结果 */
    record PayCreateResult(boolean success, String payUrl, String tradeNo, String message) {
        public static PayCreateResult done(String tradeNo) {
            return new PayCreateResult(true, null, tradeNo, null);
        }

        public static PayCreateResult pending(String payUrl, String tradeNo) {
            return new PayCreateResult(true, payUrl, tradeNo, null);
        }

        public static PayCreateResult fail(String message) {
            return new PayCreateResult(false, null, null, message);
        }
    }

    /** 异步回调解析结果 */
    record PayNotifyResult(boolean paid, Long orderId, String tradeNo, String message) {
    }
}
