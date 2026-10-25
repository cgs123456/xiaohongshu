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

    /**
     * 主动查询渠道侧支付结果（对账补偿）：跳转类渠道的异步回调可能丢失，
     * 定时任务对超时未回调的支付单调用本方法核实，防止"用户已付款但订单停留待支付"。
     */
    PayQueryStatus queryPayment(String tradeNo);

    /** 渠道侧支付状态 */
    enum PayQueryStatus {
        /** 渠道确认已支付（需补完成订单） */
        PAID,
        /** 渠道确认未支付/查无此单（超时后可关单） */
        NOT_FOUND,
        /** 渠道处理中（继续等待回调/下轮查单） */
        PENDING
    }

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
