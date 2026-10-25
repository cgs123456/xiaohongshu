package com.itcast.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itcast.constant.MqConstant;
import com.itcast.constant.RedisConstant;
import com.itcast.context.UserContext;
import com.itcast.mapper.CartMapper;
import com.itcast.mapper.CouponMapper;
import com.itcast.model.dto.BuyDto;
import com.itcast.model.dto.CheckoutDto;
import com.itcast.model.dto.CheckoutOrderDto;
import com.itcast.model.pojo.Cart;
import com.itcast.model.pojo.Coupon;
import com.itcast.model.pojo.Product;
import com.itcast.result.Result;
import com.itcast.service.CheckoutService;
import com.itcast.service.CouponService;
import com.itcast.service.ProductService;
import com.itcast.util.IsExpireUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 购物车结算服务：多商品一单。
 * 流程：校验收货信息 → Redis Lua 逐项扣库存（失败回补已扣项）→
 *       核销优惠券（门槛校验，失败回补库存）→ 发 MQ（MySQL 逐项扣减 + order 服务建单）→
 *       清理已结算购物车项。任一步失败都有对应补偿。
 */
@Service
@Slf4j
public class CheckoutServiceImpl implements CheckoutService {

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private ProductService productService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponMapper couponMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Result<String> checkout(CheckoutDto dto) {
        Integer userId = UserContext.getUserId();
        if (userId == null) {
            return Result.failure("用户未登录");
        }
        if (dto == null || isBlank(dto.getReceiverName()) || isBlank(dto.getReceiverPhone()) || isBlank(dto.getAddress())) {
            return Result.failure("收货信息不完整");
        }

        // 0.请求级幂等（防双击）：同一用户 5 秒内只允许一次结算
        String idemKey = "checkout:idem:" + userId;
        Boolean first = redisTemplate.opsForValue().setIfAbsent(idemKey, "1", 5, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(first)) {
            return Result.failure("请勿重复提交，请稍后再试");
        }

        // 1.加载购物车（全部或选中项）
        List<Cart> carts = cartMapper.selectList(
                new LambdaQueryWrapper<Cart>().eq(Cart::getUserId, userId));
        if (dto.getProductIds() != null && !dto.getProductIds().isEmpty()) {
            carts = carts.stream()
                    .filter(c -> dto.getProductIds().contains(c.getProductId()))
                    .collect(java.util.stream.Collectors.toList());
        }
        if (carts.isEmpty()) {
            return Result.failure("购物车为空");
        }

        // 2.逐项扣 Redis 库存并构建快照明细；任一失败回补全部已扣项
        List<CheckoutOrderDto.CheckoutItem> items = new ArrayList<>();
        List<int[]> deducted = new ArrayList<>(); // {productId, quantity} 已扣成功的，用于回补
        BigDecimal total = BigDecimal.ZERO;
        for (Cart cart : carts) {
            int quantity = cart.getQuantity() == null || cart.getQuantity() < 1 ? 1 : cart.getQuantity();
            Product product = productService.selectById(cart.getProductId());
            if (product == null) {
                rollbackRedisStock(deducted);
                return Result.failure("商品不存在或已下架：" + cart.getProductName());
            }
            long remain = productService.deductRedisStock(product.getId(), quantity);
            if (remain < 0) {
                rollbackRedisStock(deducted);
                return Result.failure("库存不足：" + product.getName());
            }
            deducted.add(new int[]{product.getId(), quantity});

            CheckoutOrderDto.CheckoutItem item = new CheckoutOrderDto.CheckoutItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setProductImage(product.getImage());
            item.setUnitPrice(product.getPrice()); // 以 DB 当前价为快照（购物车里的价格可能过期）
            item.setQuantity(quantity);
            items.add(item);
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        }

        // 3.优惠券：门槛/有效期前置校验（给友好提示），核销走事务方法（持券+库存+核销一体）
        BigDecimal discount = BigDecimal.ZERO;
        boolean couponUsed = false;
        if (dto.getCouponId() != null) {
            Coupon coupon = couponMapper.selectById(dto.getCouponId());
            if (coupon == null) {
                rollbackRedisStock(deducted);
                return Result.failure("优惠券不存在");
            }
            if (coupon.getStock() == null || coupon.getStock() <= 0
                    || IsExpireUtil.isExpire(coupon.getStartTime(), coupon.getEndTime())) {
                rollbackRedisStock(deducted);
                return Result.failure("优惠券已失效");
            }
            BigDecimal minAmount = coupon.getMinAmount() == null ? BigDecimal.ZERO : coupon.getMinAmount();
            if (total.compareTo(minAmount) < 0) {
                rollbackRedisStock(deducted);
                return Result.failure("订单总价未满足券的使用门槛（满 " + minAmount.stripTrailingZeros().toPlainString() + " 可用）");
            }
            try {
                couponService.useCoupon(dto.getCouponId(), userId);
                discount = coupon.getDiscount() == null ? BigDecimal.ZERO : coupon.getDiscount();
                couponUsed = true;
            } catch (Exception e) {
                rollbackRedisStock(deducted);
                return Result.failure(e.getMessage() != null ? e.getMessage() : "优惠券使用失败");
            }
        }
        // 优惠不能超过总价
        if (discount.compareTo(total) > 0) {
            discount = total;
        }

        // 4.发 MQ：逐项 MySQL 扣减 + 结算建单（快照/地址/优惠随消息传递）
        CheckoutOrderDto message = new CheckoutOrderDto();
        message.setUserId(userId);
        message.setReceiverName(dto.getReceiverName().trim());
        message.setReceiverPhone(dto.getReceiverPhone().trim());
        message.setAddress(dto.getAddress().trim());
        message.setCouponId(dto.getCouponId());
        message.setDiscount(discount);
        message.setItems(items);
        String messageId = UUID.randomUUID().toString();

        boolean anyStockMsgSent = false;
        try {
            for (CheckoutOrderDto.CheckoutItem item : items) {
                BuyDto stockDto = new BuyDto();
                stockDto.setProductId(item.getProductId());
                stockDto.setQuantity(item.getQuantity());
                stockDto.setUserId(userId);
                rabbitTemplate.convertAndSend(MqConstant.UPDATE_PRODUCT_STOCK_EXCHANGE, "", stockDto);
                anyStockMsgSent = true;
            }
            MessagePostProcessor mpp = msg -> {
                msg.getMessageProperties().setMessageId(messageId);
                return msg;
            };
            rabbitTemplate.convertAndSend(MqConstant.CHECKOUT_ORDER_EXCHANGE, "", message, mpp);
        } catch (Exception e) {
            log.error("结算消息发送失败，开始补偿，userId={}", userId, e);
            // 回补 Redis 扣减
            rollbackRedisStock(deducted);
            // 已发出的 MySQL 扣减消息无法撤回，补发 restore 消息回滚
            if (anyStockMsgSent) {
                for (CheckoutOrderDto.CheckoutItem item : items) {
                    try {
                        BuyDto restoreDto = new BuyDto();
                        restoreDto.setProductId(item.getProductId());
                        restoreDto.setQuantity(item.getQuantity());
                        restoreDto.setUserId(userId);
                        restoreDto.setRestore(true);
                        rabbitTemplate.convertAndSend(MqConstant.UPDATE_PRODUCT_STOCK_EXCHANGE, "", restoreDto);
                    } catch (Exception ex) {
                        log.error("CRITICAL: 结算库存补偿消息发送失败，需人工核对，productId={}",
                                item.getProductId(), ex);
                    }
                }
            }
            // 已核销的券退还
            if (couponUsed) {
                try {
                    couponService.refundCoupon(dto.getCouponId(), userId);
                } catch (Exception ex) {
                    log.error("CRITICAL: 结算失败退券失败，需人工核对，couponId={} userId={}",
                            dto.getCouponId(), userId, ex);
                }
            }
            return Result.failure("系统繁忙，结算失败，已自动恢复库存与优惠券，请稍后重试");
        }

        // 5.清理已结算的购物车项
        try {
            List<Integer> productIds = items.stream()
                    .map(CheckoutOrderDto.CheckoutItem::getProductId).collect(java.util.stream.Collectors.toList());
            cartMapper.delete(new LambdaQueryWrapper<Cart>()
                    .eq(Cart::getUserId, userId)
                    .in(Cart::getProductId, productIds));
        } catch (Exception e) {
            // 清理失败不影响下单（购物车残留可由用户手动删除）
            log.warn("结算后清理购物车失败，userId={}", userId, e);
        }

        log.info("购物车结算成功，userId={} 商品 {} 项 总价 {} 优惠 {}，messageId={}",
                userId, items.size(), total, discount, messageId);
        return Result.success("下单成功，请在 15 分钟内完成支付");
    }

    private void rollbackRedisStock(List<int[]> deducted) {
        for (int[] pair : deducted) {
            productService.restoreRedisStock(pair[0], pair[1]);
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
