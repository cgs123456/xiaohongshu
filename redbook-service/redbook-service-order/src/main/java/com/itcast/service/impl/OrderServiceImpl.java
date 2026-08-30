package com.itcast.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itcast.client.ProductClient;
import com.itcast.constant.MqConstant;
import com.itcast.enums.OrderStatusEnum;
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
    private RedisTemplate<String, Object> redisTemplate;

    /** 内部接口令牌（与 product 服务 internal.token 配置保持一致） */
    @Value("${internal.token:change-me-internal-token}")
    private String internalToken;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processOrderMessage(OrderDto orderDto) {
        Integer userId = orderDto.getUserId();
        if (userId == null) {
            throw new IllegalStateException("用户ID不能为空");
        }

        // 幂等性检查：使用消息messageId（由product服务发送时通过MessagePostProcessor设置UUID）
        String messageId = orderDto.getMessageId();

        // 使用Redis原子操作setIfAbsent防止并发处理（Redis单线程保证原子性），TTL 24小时
        String processedKey = ORDER_IDEMPOTENT_KEY + messageId;

        // setIfAbsent是原子操作：如果key不存在则设置并返回true，如果已存在则返回false
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(processedKey, "1", 24, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(isNew)) {
            // key已存在，说明消息已处理过或正在处理中
            log.warn("消息已处理过或正在处理中，跳过处理，messageId: {}", messageId);
            throw new IllegalStateException("消息已处理过，messageId: " + messageId);
        }

        try {
            // 执行业务逻辑（订单保存 + 发送延迟关单消息）
            saveOrderInternal(orderDto, userId);
            log.info("订单创建成功，messageId: {}", messageId);
        } catch (Exception e) {
            // 处理失败时删除幂等标记，允许消息重试
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
            throw new IllegalStateException("商品不存在");
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
                orderAttributeMapper.insert(orderAttribute);
            }
        }

        // 落库成功后向延迟交换机发送关单消息（15分钟后由 order.close.queue 消费关单）
        OrderDelayMessage delayMessage = new OrderDelayMessage();
        delayMessage.setOrderId(orderId);
        delayMessage.setProductId(orderDto.getProductId());
        delayMessage.setQuantity(order.getQuantity());
        rabbitTemplate.convertAndSend(OrderDelayMessage.DELAY_EXCHANGE,
                OrderDelayMessage.DELAY_ROUTING_KEY, delayMessage);
    }

    @Override
    public Result<List<Order>> getOrderListByUserId(Integer userId, Integer page, Integer pageSize) {
        if (userId == null) {
            return Result.failure("用户ID不能为空");
        }

        LambdaQueryWrapper<Order> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Order::getUserId, userId);
        // 不传分页参数时返回全部，兼容现状
        if (page != null && pageSize != null) {
            if (page < 1 || pageSize < 1 || pageSize > 100) {
                return Result.failure("分页参数错误");
            }
            queryWrapper.last("LIMIT " + pageSize + " OFFSET " + (long) (page - 1) * pageSize);
        }
        List<Order> orders = orderMapper.selectList(queryWrapper);

        return Result.success(orders);
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
            // 回补商品库存（Feign 调用 product 服务，失败仅记录日志，不阻断关单）
            try {
                if (order.getProductId() != null) {
                    int quantity = order.getQuantity() == null ? 1 : order.getQuantity();
                    productClient.restoreStock(internalToken, order.getProductId(), quantity);
                }
            } catch (Exception e) {
                log.error("回补库存失败，orderId={} productId={}", orderId, order.getProductId(), e);
            }
        } catch (Exception e) {
            log.error("关单逻辑执行异常，orderId={}", orderId, e);
        }
    }
}
