package com.itcast.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itcast.client.ProductClient;
import com.itcast.enums.OrderStatusEnum;
import com.itcast.exception.DuplicateMessageException;
import com.itcast.mapper.OrderAttributeMapper;
import com.itcast.mapper.OrderMapper;
import com.itcast.model.dto.OrderDelayMessage;
import com.itcast.model.dto.OrderDto;
import com.itcast.model.pojo.CustomAttribute;
import com.itcast.model.pojo.Order;
import com.itcast.model.pojo.OrderAttribute;
import com.itcast.model.pojo.Product;
import com.itcast.result.Result;
import com.itcast.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    /** 订单创建幂等 key 前缀 */
    private static final String ORDER_IDEMPOTENT_KEY = "order:idempotent:";

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ProductClient productClient;

    @Autowired
    private OrderAttributeMapper orderAttributeMapper;

    @Autowired
    private com.itcast.mapper.OrderItemMapper orderItemMapper;

    @Autowired
    private com.itcast.mapper.PaymentMapper paymentMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 内部接口令牌（与 product 服务 internal.token 配置保持一致） */
    @Value("${internal.token:change-me-internal-token}")
    private String internalToken;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processOrderMessage(OrderDto orderDto) {
        Integer userId = orderDto.getUserId();
        if (userId == null) {
            // 毒消息：重试不可能成功，IllegalArgumentException 让消费者直接送死信
            throw new IllegalArgumentException("用户ID不能为空");
        }

        // 幂等性检查：使用消息messageId（由product服务发送时通过MessagePostProcessor设置UUID）
        String messageId = orderDto.getMessageId();

        // 使用Redis原子操作setIfAbsent防止并发处理（Redis单线程保证原子性），TTL 24小时
        String processedKey = ORDER_IDEMPOTENT_KEY + messageId;

        // setIfAbsent是原子操作：如果key不存在则设置并返回true，如果已存在则返回false
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(processedKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(isNew)) {
            // key已存在，说明消息已处理过或正在处理中——专用异常，消费者据此 ACK 丢弃
            log.warn("消息已处理过或正在处理中，跳过处理，messageId: {}", messageId);
            throw new DuplicateMessageException("消息已处理过，messageId: " + messageId);
        }

        try {
            // 执行业务逻辑（订单保存 + 注册事务提交后回调发送延迟关单消息）
            saveOrderInternal(orderDto, userId);
            log.info("订单创建成功，messageId: {}", messageId);
        } catch (Exception e) {
            // 处理失败时删除幂等标记，允许消息重试
            redisTemplate.delete(processedKey);
            throw e;
        }
    }

    /**
     * 购物车结算建单：主表（快照/地址/金额拆解/优惠券）+ 明细，同一事务
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processCheckoutMessage(String messageId, com.itcast.model.dto.CheckoutOrderDto dto) {
        Integer userId = dto.getUserId();
        if (userId == null || dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new IllegalArgumentException("结算消息缺少用户或商品明细");
        }

        // 幂等：messageId 由 product 服务生成（结算请求 UUID）
        String processedKey = ORDER_IDEMPOTENT_KEY + messageId;
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(processedKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(isNew)) {
            log.warn("结算消息已处理过，跳过，messageId: {}", messageId);
            throw new DuplicateMessageException("结算消息已处理过，messageId: " + messageId);
        }

        try {
            // 金额：总价按明细累加（以 product 侧快照价为准），实付 = 总价 - 优惠
            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            for (com.itcast.model.dto.CheckoutOrderDto.CheckoutItem item : dto.getItems()) {
                java.math.BigDecimal price = item.getUnitPrice() == null
                        ? java.math.BigDecimal.ZERO : item.getUnitPrice();
                total = total.add(price.multiply(java.math.BigDecimal.valueOf(item.getQuantity() == null ? 1 : item.getQuantity())));
            }
            java.math.BigDecimal discount = dto.getDiscount() == null ? java.math.BigDecimal.ZERO : dto.getDiscount();
            if (discount.compareTo(total) > 0) {
                discount = total;
            }
            java.math.BigDecimal finalPrice = total.subtract(discount);

            Order order = new Order();
            order.setUserId(userId);
            order.setStatus(OrderStatusEnum.DUE.getCode());
            order.setTotalPrice(total);
            order.setDiscountPrice(discount);
            order.setFinalPrice(finalPrice);
            order.setCouponId(dto.getCouponId());
            order.setReceiverName(dto.getReceiverName());
            order.setReceiverPhone(dto.getReceiverPhone());
            order.setAddress(dto.getAddress());
            orderMapper.insert(order);

            for (com.itcast.model.dto.CheckoutOrderDto.CheckoutItem item : dto.getItems()) {
                com.itcast.model.pojo.OrderItem orderItem = new com.itcast.model.pojo.OrderItem();
                orderItem.setOrderId(order.getId());
                orderItem.setProductId(item.getProductId());
                orderItem.setProductName(item.getProductName());
                orderItem.setProductImage(item.getProductImage());
                orderItem.setUnitPrice(item.getUnitPrice());
                orderItem.setQuantity(item.getQuantity() == null ? 1 : item.getQuantity());
                orderItemMapper.insert(orderItem);
            }

            // 事务提交后发 15 分钟延迟关单消息
            OrderDelayMessage delayMessage = new OrderDelayMessage();
            delayMessage.setOrderId(order.getId());
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                        new org.springframework.transaction.support.TransactionSynchronization() {
                            @Override
                            public void afterCommit() {
                                sendCloseOrderDelayMessage(delayMessage);
                            }
                        });
            } else {
                sendCloseOrderDelayMessage(delayMessage);
            }
            log.info("结算订单创建成功，orderId={} total={} discount={} messageId={}",
                    order.getId(), total, discount, messageId);
        } catch (Exception e) {
            redisTemplate.delete(processedKey);
            throw e;
        }
    }

    /**
     * 保存订单的内部方法（提取公共逻辑）
     */
    private void saveOrderInternal(OrderDto orderDto, Integer userId) {
        // 获取商品信息
        Product product = productClient.getProductById(internalToken, orderDto.getProductId()).getData();
        if (product == null) {
            // 可能是 Feign 瞬时故障（此时订单一定购买过该商品），交给消费者重试
            throw new IllegalStateException("商品不存在或查询失败");
        }

        // 保存订单（quantity 从消息透传，默认1）
        Order order = new Order();
        order.setProductId(orderDto.getProductId());
        order.setUserId(userId);
        order.setQuantity(orderDto.getQuantity() == null ? 1 : orderDto.getQuantity());
        order.setStatus(OrderStatusEnum.DUE.getCode());
        // 使用商品实际价格
        order.setFinalPrice(java.math.BigDecimal.valueOf(product.getPrice()));
        orderMapper.insert(order);

        // 保存订单属性
        Long orderId = order.getId();
        if (orderId == null) {
            throw new IllegalStateException("订单ID生成失败");
        }

        List<CustomAttribute> selectAttributes = orderDto.getSelectAttributes();
        if (selectAttributes != null && !selectAttributes.isEmpty()) {
            List<OrderAttribute> attributes = new java.util.ArrayList<>();
            for (CustomAttribute selectAttribute : selectAttributes) {
                if (selectAttribute == null) {
                    log.warn("订单属性为空，跳过");
                    continue;
                }

                List<String> values = selectAttribute.getValue();
                if (values == null || values.isEmpty()) {
                    log.warn("订单属性值为空，跳过属性：{}", selectAttribute.getLabel());
                    continue;
                }

                OrderAttribute orderAttribute = new OrderAttribute();
                orderAttribute.setOrderId(orderId);
                orderAttribute.setLabel(selectAttribute.getLabel());
                orderAttribute.setValue(values.get(0));
                attributes.add(orderAttribute);
            }
            // 先收集后插入（Mapper 无 saveBatch，仍逐条；此处量级为单品属性个数，可接受）
            if (!attributes.isEmpty()) {
                attributes.forEach(orderAttributeMapper::insert);
            }
        }

        // 延迟关单消息必须在事务提交后发送：事务内发送若回滚会产生孤儿关单消息
        OrderDelayMessage delayMessage = new OrderDelayMessage();
        delayMessage.setOrderId(orderId);
        delayMessage.setProductId(orderDto.getProductId());
        delayMessage.setQuantity(order.getQuantity());
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            sendCloseOrderDelayMessage(delayMessage);
                        }
                    });
        } else {
            sendCloseOrderDelayMessage(delayMessage);
        }
    }

    private void sendCloseOrderDelayMessage(OrderDelayMessage delayMessage) {
        try {
            rabbitTemplate.convertAndSend(OrderDelayMessage.DELAY_EXCHANGE,
                    OrderDelayMessage.DELAY_ROUTING_KEY, delayMessage);
        } catch (Exception e) {
            // 发送失败不影响订单落库：兜底扫描任务（OrderTimeoutTask）会捞起超时未支付订单关单
            log.error("发送延迟关单消息失败，依赖定时任务兜底，orderId={}", delayMessage.getOrderId(), e);
        }
    }

    @Override
    public Result<List<com.itcast.model.vo.OrderVo>> getOrderListByUserId(Integer userId, Integer page, Integer pageSize) {
        if (userId == null) {
            return Result.failure("用户ID不能为空");
        }
        // 默认分页 1/20（此前不传参全量返回，老用户订单多时全表拉取）
        int p = (page == null || page < 1) ? 1 : page;
        int size = (pageSize == null || pageSize < 1) ? 20 : Math.min(pageSize, 100);

        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Order::getUserId, userId)
                .orderByDesc(Order::getId)
                .last("LIMIT " + size + " OFFSET " + (long) (p - 1) * size);
        List<Order> orders = orderMapper.selectList(queryWrapper);
        if (orders.isEmpty()) {
            return Result.success(new java.util.ArrayList<>());
        }

        // 批量查明细（一次 in 查询，避免每单一查）
        List<Long> orderIds = orders.stream().map(Order::getId).collect(java.util.stream.Collectors.toList());
        List<com.itcast.model.pojo.OrderItem> allItems = orderItemMapper.selectList(
                new LambdaQueryWrapper<com.itcast.model.pojo.OrderItem>()
                        .in(com.itcast.model.pojo.OrderItem::getOrderId, orderIds));
        java.util.Map<Long, List<com.itcast.model.pojo.OrderItem>> itemMap = allItems.stream()
                .collect(java.util.stream.Collectors.groupingBy(com.itcast.model.pojo.OrderItem::getOrderId));

        List<com.itcast.model.vo.OrderVo> vos = new java.util.ArrayList<>(orders.size());
        for (Order order : orders) {
            com.itcast.model.vo.OrderVo vo = new com.itcast.model.vo.OrderVo();
            vo.setOrder(order);
            vo.setItems(itemMap.getOrDefault(order.getId(), java.util.Collections.emptyList()));
            // 旧单（无明细）兼容：把主表遗留的 product_id/quantity 映射成一条明细
            if ((vo.getItems().isEmpty()) && order.getProductId() != null) {
                com.itcast.model.pojo.OrderItem legacy = new com.itcast.model.pojo.OrderItem();
                legacy.setOrderId(order.getId());
                legacy.setProductId(order.getProductId());
                legacy.setQuantity(order.getQuantity() == null ? 1 : order.getQuantity());
                legacy.setUnitPrice(order.getFinalPrice());
                vo.setItems(java.util.Collections.singletonList(legacy));
            }
            vos.add(vo);
        }
        return Result.success(vos);
    }

    @Override
    public Result<Void> payOrder(Long orderId, Integer userId) {
        if (orderId == null || userId == null) {
            return Result.failure("参数错误");
        }
        Order order = orderMapper.selectById(orderId);
        if (order == null || !userId.equals(order.getUserId())) {
            return Result.failure("订单不存在");
        }
        if (order.getStatus() == null || order.getStatus() != OrderStatusEnum.DUE.getCode()) {
            return Result.failure("订单状态不可支付");
        }
        // 乐观更新：仅当仍为待支付时置为已支付（与关单竞态时只有一方成功）
        Order update = new Order();
        update.setId(orderId);
        update.setStatus(OrderStatusEnum.PAID.getCode());
        update.setPayTime(java.time.LocalDateTime.now());
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getId, orderId).eq(Order::getStatus, OrderStatusEnum.DUE.getCode());
        int rows = orderMapper.update(update, wrapper);
        if (rows == 0) {
            return Result.failure("订单状态已变化，支付失败（可能已超时取消）");
        }
        // 落支付流水（模拟支付；失败只记日志，订单状态以乐观更新为准）
        try {
            com.itcast.model.pojo.Payment payment = new com.itcast.model.pojo.Payment();
            payment.setOrderId(orderId);
            payment.setUserId(userId);
            payment.setAmount(order.getFinalPrice() == null ? java.math.BigDecimal.ZERO : order.getFinalPrice());
            payment.setPayMethod("MOCK");
            payment.setStatus(1);
            payment.setPayTime(java.time.LocalDateTime.now());
            paymentMapper.insert(payment);
        } catch (Exception e) {
            log.error("支付流水落库失败（不影响订单状态），orderId={}", orderId, e);
        }
        log.info("订单支付成功（模拟支付），orderId={} userId={} amount={}", orderId, userId, order.getFinalPrice());
        return Result.success(null);
    }

    @Override
    public void closeOrderIfDue(Long orderId) {
        try {
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                log.warn("关单失败，订单不存在，orderId={}", orderId);
                return;
            }
            // 仅待支付订单需要取消
            if (order.getStatus() == null || order.getStatus() != OrderStatusEnum.DUE.getCode()) {
                log.info("订单已不是待支付状态，无需关单，orderId={} status={}", orderId, order.getStatus());
                return;
            }
            // 乐观更新：仅当仍为待支付时改为已取消，防止用户在关单瞬间完成支付
            Order update = new Order();
            update.setId(order.getId());
            update.setStatus(OrderStatusEnum.CANCEL.getCode());
            LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Order::getId, order.getId()).eq(Order::getStatus, OrderStatusEnum.DUE.getCode());
            int rows = orderMapper.update(update, wrapper);
            if (rows == 0) {
                log.info("订单状态已变化，取消关单，orderId={}", orderId);
                return;
            }
            log.info("订单超时未支付，已取消，orderId={}", orderId);

            // 回补库存：优先按明细逐项回补；旧单（无明细）用主表遗留字段。
            // 失败进 Redis 重试队列由定时任务补偿——此时订单已是 CANCEL，
            // 兜底扫描（status=0）不会再命中，不重试库存就永久少一份
            List<com.itcast.model.pojo.OrderItem> items = orderItemMapper.selectList(
                    new LambdaQueryWrapper<com.itcast.model.pojo.OrderItem>()
                            .eq(com.itcast.model.pojo.OrderItem::getOrderId, orderId));
            if (!items.isEmpty()) {
                for (com.itcast.model.pojo.OrderItem item : items) {
                    restoreStockQuietly(orderId, item.getProductId(),
                            item.getQuantity() == null ? 1 : item.getQuantity());
                }
            } else if (order.getProductId() != null) {
                restoreStockQuietly(orderId, order.getProductId(),
                        order.getQuantity() == null ? 1 : order.getQuantity());
            }

            // 退还优惠券（结算时核销的券随取消退回）
            if (order.getCouponId() != null) {
                try {
                    productClient.refundCoupon(internalToken, order.getUserId(), order.getCouponId());
                } catch (Exception e) {
                    log.error("退券失败，进入重试队列，orderId={} couponId={}", orderId, order.getCouponId(), e);
                    enqueueRetryTask("C:" + order.getUserId() + ":" + order.getCouponId());
                }
            }
        } catch (Exception e) {
            log.error("关单逻辑执行异常，orderId={}", orderId, e);
        }
    }

    private void restoreStockQuietly(Long orderId, Integer productId, int quantity) {
        try {
            productClient.restoreStock(internalToken, productId, quantity);
        } catch (Exception e) {
            log.error("回补库存失败，进入重试队列，orderId={} productId={}", orderId, productId, e);
            enqueueRetryTask("S:" + productId + ":" + quantity);
        }
    }

    /** 补偿重试队列（Redis List）。元素格式：S:{productId}:{quantity} 库存回补；C:{userId}:{couponId} 退券 */
    private static final String RETRY_TASK_KEY = "order:compensate:retry";

    private void enqueueRetryTask(String task) {
        try {
            redisTemplate.opsForList().leftPush(RETRY_TASK_KEY, task);
        } catch (Exception ex) {
            // Redis 也失败时只能依赖人工核对 rb_order 已取消订单与 product 库存/优惠券流水
            log.error("补偿任务入队失败（需人工核对），task={}", task, ex);
        }
    }

    /**
     * 供定时任务调用的重试入口：弹出一条补偿任务并执行，成功返回 true（队列空也返回 true），失败返回 false
     */
    @Override
    public boolean retryOneStockRestore() {
        Object task = redisTemplate.opsForList().rightPop(RETRY_TASK_KEY);
        if (task == null) {
            return true;
        }
        String t = task.toString();
        try {
            String[] parts = t.split(":");
            if (t.startsWith("S:")) {
                productClient.restoreStock(internalToken, Integer.valueOf(parts[1]), Integer.valueOf(parts[2]));
            } else if (t.startsWith("C:")) {
                productClient.refundCoupon(internalToken, Integer.valueOf(parts[1]), Long.valueOf(parts[2]));
            } else {
                log.error("未知补偿任务类型，丢弃，task={}", t);
                return true;
            }
            log.info("补偿任务重试成功，task={}", t);
            return true;
        } catch (Exception e) {
            log.error("补偿任务重试失败，重新入队，task={}", t, e);
            redisTemplate.opsForList().leftPush(RETRY_TASK_KEY, t);
            return false;
        }
    }
}
