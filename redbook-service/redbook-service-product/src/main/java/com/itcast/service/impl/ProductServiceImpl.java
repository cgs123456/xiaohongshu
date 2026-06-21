package com.itcast.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itcast.constant.MqConstant;
import com.itcast.constant.RedisConstant;
import com.itcast.context.UserContext;
import com.itcast.mapper.ProductBrowseMapper;
import com.itcast.mapper.ProductMapper;
import com.itcast.mapper.ShopMapper;
import com.itcast.model.dto.BuyDto;
import com.itcast.model.dto.ProductDto;
import com.itcast.model.pojo.Product;
import com.itcast.model.pojo.ProductAttribute;
import com.itcast.model.pojo.Shop;
import com.itcast.model.vo.ProductVo;
import com.itcast.result.Result;
import com.itcast.service.ProductService;
import com.itcast.util.HttpUtil;
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

    @Override
    public Result<List<Product>> getProductList() {
        List<Product> products = productMapper.selectList(null);
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
        // 1.上传商品
        Product product = new Product();
        // 先插入商品获取 ID
        productMapper.insert(product);
        
        // 2.上传商品属性
        ProductAttribute productAttribute = productDto.getProductAttribute();
        productAttribute.setProductId(product.getId());
        mongoTemplate.insert(productAttribute);

        return Result.success(null);
    }

    @Override
    public Result<Void> updateProduct(ProductDto productDto) {
        Product product = new Product();
        BeanUtils.copyProperties(productDto, product);
        productMapper.updateById(product);
        return Result.success(null);
    }

    @Override
    public Result<Void> buyProduct(BuyDto buyDto) {
        String key = RedisConstant.PRODUCT_STOCK_CACHE + buyDto.getProductId();

        // 0.使用分布式锁做缓存预热
        RLock lock = redissonClient.getLock("buyProduct:" + buyDto.getProductId());
        try {
            lock.lock();
            // 1.判断redis中是否存在库存
            if (!redisTemplate.hasKey(key)) {
                // 2.将库存预热缓存在redis中
                Product product = productMapper.selectById(buyDto.getProductId());
                if (product == null) {
                    return Result.failure("商品不存在");
                }
                Integer stock = product.getStock();
                redisTemplate.opsForValue().set(key, stock);
                redisTemplate.expire(key, 60 * 60 * 24, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.error("缓存击穿");
            return Result.failure("商品已售罄");
        } finally {
            lock.unlock();
        }

        // 3.使用lua脚本保证判断库存数量和减库存的原子性
        String luaScript = "local stock = redis.call('get', KEYS[1]); " +
                "if stock == false or stock == nil then " +
                "    return -2; " +  // 库存不存在
                "end " +
                "stock = tonumber(stock); " +
                "local quantity = tonumber(ARGV[1]); " +  // ARGV[1]应该是要购买的数量
                "if stock >= quantity then " +
                "    redis.call('decrby', KEYS[1], quantity); " +
                "    return stock - quantity; " +  // 返回剩余库存
                "else " +
                "    return -1; " +  // 库存不足
                "end";
        RedisScript<Long> script = RedisScript.of(luaScript, Long.class);
        Long result = redisTemplate.execute(script, Collections.singletonList(key), 1L);
        if (result == -1) {
            return Result.failure("库存不足");
        }

        // 4.异步更新mysql中的库存
        rabbitTemplate.convertAndSend(MqConstant.UPDATE_PRODUCT_STOCK_EXCHANGE, "", buyDto.getProductId());

        // 5.发送消息给订单服务
        rabbitTemplate.convertAndSend(MqConstant.SAVE_ORDER_EXCHANGE, "", buyDto);

        return Result.success(null);
    }

    @Override
    public Result<String> buyProduct(String message) {
        try {
            // 获取当前用户 ID
            Integer userId = UserContext.getUserId();
            
            // 调用 Agent 聊天接口，在请求头中传递 userId
            String url = "http://localhost:9999/agent/chat";
            String response = HttpUtil.postWithUserId(url, message, userId);
            
            log.info("用户 {} 的购买咨询: {}, AI回复: {}", userId, message, response);
            return Result.success(response);
        } catch (Exception e) {
            log.error("调用 Agent 服务失败", e);
            return Result.failure("调用 Agent 服务失败");
        }
    }

    @Override
    public Product selectById(Integer id) {
        return productMapper.selectById(id);
    }

    @Override
    public void updateById(Product product) {
        productMapper.updateById(product);
    }
}
