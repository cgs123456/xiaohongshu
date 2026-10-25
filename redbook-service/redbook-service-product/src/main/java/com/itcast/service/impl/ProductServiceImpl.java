package com.itcast.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itcast.agent.ShoppingAgentService;
import com.itcast.constant.MqConstant;
import com.itcast.constant.RedisConstant;
import com.itcast.context.UserContext;
import com.itcast.mapper.ProductBrowseMapper;
import com.itcast.mapper.ProductMapper;
import com.itcast.mapper.ShopMapper;
import com.itcast.model.dto.BuyDto;
import com.itcast.model.dto.ProductDto;
import com.itcast.model.dto.SkuDto;
import com.itcast.model.pojo.Sku;
import com.itcast.util.SkuKeyUtil;
import com.itcast.model.pojo.Product;
import com.itcast.model.pojo.ProductAttribute;
import com.itcast.model.pojo.Shop;
import com.itcast.model.vo.ProductVo;
import com.itcast.result.Result;
import com.itcast.service.ProductService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private com.itcast.mapper.SkuMapper skuMapper;

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private ProductBrowseMapper productBrowseMapper;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ShoppingAgentService shoppingAgentService;

    @Override
    public Result<List<Product>> getProductList() {
        return getProductList(null, null);
    }

    @Override
    public Result<List<Product>> recommend(int limit) {
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .gt(Product::getStock, 0)
                        .orderByDesc(Product::getId)
                        .last("LIMIT " + Math.min(Math.max(limit, 1), 50)));
        return Result.success(products);
    }

    @Override
    public Result<List<Product>> getProductList(Integer page, Integer pageSize) {
        // 不传分页参数时返回全部，兼容现状
        if (page == null || pageSize == null) {
            List<Product> products = productMapper.selectList(null);
            return Result.success(products);
        }
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            return Result.failure("分页参数错误");
        }
        // 使用 LIMIT/OFFSET 分页（page/pageSize 已校验为整数，无注入风险）
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.last("LIMIT " + pageSize + " OFFSET " + (long) (page - 1) * pageSize);
        List<Product> products = productMapper.selectList(wrapper);
        return Result.success(products);
    }

    @Override
    public Result<ProductVo> getProduct(Integer productId) {
        // 1.获取商品信息
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return Result.failure("商品不存在");
        }
        // 2.获取店铺信息
        Shop shop = shopMapper.selectById(product.getShopId());
        // 获取商品属性
        ProductAttribute productAttribute
                = mongoTemplate.findOne(new Query(Criteria.where("productId").is(product.getId())), ProductAttribute.class);
        // 3.设置vo
        ProductVo productVo = new ProductVo();
        productVo.setProduct(product);
        productVo.setShop(shop);
        if (productAttribute != null) productVo.setCustomAttributes(productAttribute.getCustomAttributes());
//        // 4.保存浏览记录
//        try {
//            ProductBrowse productBrowse = new ProductBrowse();
//            productBrowse.setProductId(productId);
//            productBrowse.setUserId(UserContext.getUserId());
//            productBrowseMapper.insert(productBrowse);
//        } catch (Exception e) {
//            log.error("用户已经访问过，不需要再次插入数据库");
//        }
        return Result.success(productVo);
    }

    @Override
    public Result<List<Product>> getProductByShop(Integer productId) {
        // 1.根据产品id获取店铺id
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return Result.failure("商品不存在");
        }
        Integer shopId = product.getShopId();
        // 2.根据店铺id获取该店铺所有产品
        LambdaQueryWrapper<Product> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Product::getShopId, shopId);
        List<Product> products = productMapper.selectList(queryWrapper);
        // 3.过滤该产品
        products = products.stream().filter(
                product2 -> !product2.getId().equals(productId)).collect(Collectors.toList());
        return Result.success(products);
    }

    @Override
    public Result<Void> postProduct(ProductDto productDto) {
        // 1.参数校验
        if (productDto == null || productDto.getName() == null || productDto.getName().isBlank()
                || productDto.getPrice() == null) {
            return Result.failure("商品名称和价格不能为空");
        }
        // 2.上传商品（必须拷贝 DTO 字段，此前漏拷导致插入全空商品）
        Product product = new Product();
        BeanUtils.copyProperties(productDto, product);
        product.setId(null); // 主键由数据库自增，忽略客户端传入
        product.setUserId(UserContext.getUserId()); // 属主为当前登录用户
        productMapper.insert(product);

        // 3.上传商品属性（可选）
        ProductAttribute productAttribute = productDto.getProductAttribute();
        if (productAttribute != null) {
            productAttribute.setProductId(product.getId());
            mongoTemplate.insert(productAttribute);
        }

        // 4.创建 SKU（库存最小扣减口径）：声明了 skus 按规格建；否则建默认 SKU 承载商品级库存
        int totalStock = 0;
        if (productDto.getSkus() != null && !productDto.getSkus().isEmpty()) {
            for (SkuDto skuDto : productDto.getSkus()) {
                if (skuDto == null || skuDto.getStock() == null || skuDto.getStock() < 0) {
                    continue;
                }
                Sku sku = new Sku();
                sku.setProductId(product.getId());
                sku.setAttrsKey(SkuKeyUtil.build(skuDto.getAttributes()));
                sku.setStock(skuDto.getStock());
                skuMapper.insert(sku);
                totalStock += skuDto.getStock();
            }
        }
        if (totalStock == 0) {
            Sku sku = new Sku();
            sku.setProductId(product.getId());
            sku.setAttrsKey("");
            sku.setStock(product.getStock() == null ? 0 : product.getStock());
            skuMapper.insert(sku);
            totalStock = sku.getStock();
        } else {
            // 聚合库存 = SKU 之和（列表展示口径）
            Product stockSync = new Product();
            stockSync.setId(product.getId());
            stockSync.setStock(totalStock);
            productMapper.updateById(stockSync);
        }

        return Result.success(null);
    }

    @Override
    public Result<Void> updateProduct(ProductDto productDto) {
        if (productDto.getId() == null) {
            return Result.failure("商品ID不能为空");
        }
        Product existing = productMapper.selectById(productDto.getId());
        if (existing == null) {
            return Result.failure("商品不存在");
        }
        // 属主校验：仅发布者可修改自己的商品
        Integer userId = UserContext.getUserId();
        if (!existing.getUserId().equals(userId)) {
            return Result.failure("只能修改自己发布的商品");
        }
        Product product = new Product();
        BeanUtils.copyProperties(productDto, product);
        product.setUserId(existing.getUserId()); // 不允许转移属主
        productMapper.updateById(product);
        return Result.success(null);
    }

    @Override
    public Result<Void> buyProduct(BuyDto buyDto) {
        // 0.校验购买数量（默认1，允许范围 1~999）
        Integer quantity = buyDto.getQuantity();
        if (quantity == null) {
            quantity = 1;
        }
        if (quantity < 1 || quantity > 999) {
            return Result.failure("购买数量必须在1-999之间");
        }
        buyDto.setQuantity(quantity);

        // 0.4 解析 SKU：无属性购买取默认 SKU（attrs_key=""），Agent/直购路径统一在此落口径
        Sku sku = buyDto.getSkuId() != null
                ? skuMapper.selectById(buyDto.getSkuId())
                : resolveSku(buyDto.getProductId(), null);
        if (sku == null) {
            return Result.failure("商品不存在或规格已下架");
        }
        buyDto.setSkuId(sku.getId());
        buyDto.setProductId(sku.getProductId());

        // 0.5 用户级请求幂等（防双击/重放）：userId+skuId 5 秒去重窗口。
        // 消息级 messageId 幂等只能防 broker 重投，防不了同一用户连发两次请求；
        // 失败路径会删除该键（见下方各失败分支），成功的请求由 TTL 自然过期
        String requestKey = buyDto.getUserId() == null ? null
                : "buy:idem:" + buyDto.getUserId() + ":" + sku.getId();
        if (requestKey != null) {
            Boolean first = redisTemplate.opsForValue().setIfAbsent(requestKey, "1", 5, TimeUnit.SECONDS);
            if (Boolean.FALSE.equals(first)) {
                return Result.failure("请勿重复提交，请稍后再试");
            }
        }

        // 1&3.Redis Lua 原子扣减（含预热，与结算链路共用，SKU 级）
        Long result = deductRedisStockInternal(sku.getId(), quantity);
        if (result == null || result < 0) {
            // -1库存不足 / -2 SKU不存在，统一按库存不足处理；失败的请求立即释放幂等键
            if (requestKey != null) {
                redisTemplate.delete(requestKey);
            }
            return Result.failure("库存不足");
        }

        // 4&5.发送两条消息（MySQL 扣减 + 创建订单）。任一失败必须补偿 Redis 扣减；
        //     若第一条已发出无法撤回，补发一条 restore 消息回滚 MySQL 扣减。
        //     （进程在 Lua 之后、发送之前崩溃的窗口仍存在，完整方案需本地消息表 Outbox，
        //      当前用同步补偿覆盖最常见的 broker 不可用场景）
        boolean stockMsgSent = false;
        try {
            rabbitTemplate.convertAndSend(MqConstant.UPDATE_PRODUCT_STOCK_EXCHANGE, "", buyDto,
                    withMessageId());
            stockMsgSent = true;

            rabbitTemplate.convertAndSend(MqConstant.SAVE_ORDER_EXCHANGE, "", buyDto, withMessageId());
        } catch (Exception e) {
            log.error("下单消息发送失败，开始补偿，productId={} quantity={}",
                    buyDto.getProductId(), quantity, e);
            // 回补 Redis 扣减（SKU 级）
            try {
                restoreRedisStock(sku.getId(), quantity);
            } catch (Exception ex) {
                log.error("CRITICAL: Redis 库存回补失败，需人工核对，skuId={} quantity={}", sku.getId(), quantity, ex);
            }
            // MySQL 扣减消息已发出则补发回滚消息
            if (stockMsgSent) {
                try {
                    BuyDto restoreDto = new BuyDto();
                    restoreDto.setProductId(buyDto.getProductId());
                    restoreDto.setQuantity(quantity);
                    restoreDto.setUserId(buyDto.getUserId());
                    restoreDto.setRestore(true);
                    rabbitTemplate.convertAndSend(MqConstant.UPDATE_PRODUCT_STOCK_EXCHANGE, "", restoreDto,
                            withMessageId());
                } catch (Exception ex) {
                    log.error("CRITICAL: MySQL 库存补偿消息发送失败，需人工核对，productId={} quantity={}",
                            buyDto.getProductId(), quantity, ex);
                }
            }
            if (requestKey != null) {
                redisTemplate.delete(requestKey);
            }
            return Result.failure("系统繁忙，下单失败，已自动恢复库存，请稍后重试");
        }

        return Result.success(null);
    }

    /** 消息唯一 ID（UUID）：消费端幂等键与重试计数的依据，所有库存/订单消息必须携带 */
    private org.springframework.amqp.core.MessagePostProcessor withMessageId() {
        return msg -> {
            msg.getMessageProperties().setMessageId(java.util.UUID.randomUUID().toString());
            return msg;
        };
    }

    @Override
    public Result<String> agentChat(String conversationId, String message) {
        // 新接口：支持多轮对话（前端传入 conversationId 维持上下文）
        try {
            log.info("用户 {} 多轮对话 conversationId={} message={}",
                    UserContext.getUserId(), conversationId, message);
            String reply = shoppingAgentService.chat(conversationId, message);
            return Result.success(reply);
        } catch (Exception e) {
            log.error("Agent 处理失败", e);
            return Result.failure("Agent 处理失败：" + e.getMessage());
        }
    }

    /**
     * Redis 库存 Lua 脚本（判断库存与扣减原子执行）：
     * 返回扣减后剩余库存；-1 库存不足；-2 库存 key 不存在
     */
    private static final String STOCK_DEDUCT_LUA =
            "local stock = redis.call('get', KEYS[1]); " +
            "if stock == false or stock == nil then " +
            "    return -2; " +
            "end " +
            "stock = tonumber(stock); " +
            "local quantity = tonumber(ARGV[1]); " +
            "if stock >= quantity then " +
            "    redis.call('decrby', KEYS[1], quantity); " +
            "    return stock - quantity; " +
            "else " +
            "    return -1; " +
            "end";

    @Override
    public long deductRedisStock(Integer skuId, int quantity) {
        Long result = deductRedisStockInternal(skuId, quantity);
        return result == null ? -1 : result;
    }

    /**
     * 分布式锁预热 + Lua 原子扣减（buyProduct 与购物车结算共用，SKU 级）
     */
    private Long deductRedisStockInternal(Integer skuId, int quantity) {
        String key = RedisConstant.PRODUCT_SKU_STOCK_CACHE + skuId;

        // 缓存预热（tryLock 防止长时间阻塞）
        RLock lock = redissonClient.getLock("buySku:" + skuId);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("获取缓存预热锁失败，skuId={}", skuId);
                return -1L;
            }
            if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                Sku sku = skuMapper.selectById(skuId);
                if (sku == null) {
                    return -2L;
                }
                redisTemplate.opsForValue().set(key, sku.getStock() == null ? 0 : sku.getStock());
                redisTemplate.expire(key, 60 * 60 * 24, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取缓存预热锁被中断，skuId={}", skuId, e);
            return -1L;
        } catch (Exception e) {
            log.error("缓存预热异常，skuId={}", skuId, e);
            return -1L;
        } finally {
            if (locked) {
                lock.unlock();
            }
        }

        RedisScript<Long> script = RedisScript.of(STOCK_DEDUCT_LUA, Long.class);
        return redisTemplate.execute(script, Collections.singletonList(key), (long) quantity);
    }

    @Override
    public void restoreRedisStock(Integer skuId, int quantity) {
        try {
            redisTemplate.opsForValue().increment(RedisConstant.PRODUCT_SKU_STOCK_CACHE + skuId, quantity);
        } catch (Exception e) {
            log.error("CRITICAL: Redis 库存回补失败，需人工核对，skuId={} quantity={}", skuId, quantity, e);
        }
    }

    @Override
    public Sku resolveSku(Integer productId, java.util.Map<String, String> attributes) {
        if (productId == null) {
            return null;
        }
        String attrsKey = SkuKeyUtil.build(attributes);
        return skuMapper.selectOne(new LambdaQueryWrapper<Sku>()
                .eq(Sku::getProductId, productId)
                .eq(Sku::getAttrsKey, attrsKey)
                .last("LIMIT 1"));
    }

    @Override
    public Sku getSkuById(Integer skuId) {
        return skuId == null ? null : skuMapper.selectById(skuId);
    }

    @Override
    public java.util.Map<Integer, Product> selectBatchIds(java.util.List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        return productMapper.selectBatchIds(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
    }

    @Override
    public Result<java.util.List<Sku>> getSkus(Integer productId) {
        if (productId == null) {
            return Result.failure("商品ID不能为空");
        }
        return Result.success(skuMapper.selectByProductId(productId));
    }

    @Override
    public Result<List<Product>> searchProducts(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            // 无关键词时返回全部商品（最多 20 条）
            LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
            wrapper.last("LIMIT 20");
            return Result.success(productMapper.selectList(wrapper));
        }
        // 关键词超长截断，防超长字符串造成全表模糊扫描
        keyword = keyword.trim();
        if (keyword.length() > 50) {
            keyword = keyword.substring(0, 50);
        }
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Product::getName, keyword)
               .or().like(Product::getDescription, keyword)
               .or().like(Product::getBrand, keyword)
               .last("LIMIT 20");
        return Result.success(productMapper.selectList(wrapper));
    }

    @Override
    public Product selectById(Integer id) {
        return productMapper.selectById(id);
    }

    @Override
    public void updateById(Product product) {
        productMapper.updateById(product);
    }

    @Override
    public Result<Void> restoreStock(Integer skuId, int quantity) {
        if (skuId == null || quantity <= 0) {
            return Result.failure("参数错误");
        }
        try {
            Sku sku = skuMapper.selectById(skuId);
            if (sku == null) {
                log.error("回补库存失败，SKU 不存在，skuId={} quantity={}", skuId, quantity);
                return Result.failure("规格不存在");
            }
            // 1.MySQL 两级原子加库存（SKU + 商品聚合，同事务由调用方保证；此处消费链路为顺序两写）
            skuMapper.restoreStock(skuId, quantity);
            skuMapper.restoreProductStock(sku.getProductId(), quantity);
            // 2.Redis 同步加库存（key 与扣减侧完全一致；key 不存在时不预热——下次扣减会预热）
            String key = RedisConstant.PRODUCT_SKU_STOCK_CACHE + skuId;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                redisTemplate.opsForValue().increment(key, quantity);
            }
            log.info("回补库存成功，skuId={} productId={} quantity={}", skuId, sku.getProductId(), quantity);
            return Result.success(null);
        } catch (Exception e) {
            log.error("回补库存异常，skuId={} quantity={}", skuId, quantity, e);
            return Result.failure("回补库存失败");
        }
    }
}
