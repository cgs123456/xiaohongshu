package com.itcast.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itcast.context.UserContext;
import com.itcast.mapper.CartMapper;
import com.itcast.mapper.ProductMapper;
import com.itcast.model.pojo.Cart;
import com.itcast.model.pojo.Product;
import com.itcast.result.Result;
import com.itcast.service.CartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class CartServiceImpl implements CartService {

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private ProductMapper productMapper;

    /** 直接用 Mapper 解析 SKU：注入 ProductService 会形成 ShoppingAgent 循环依赖 */
    @Autowired
    private com.itcast.mapper.SkuMapper skuMapper;

    @Override
    @Transactional
    public Result<Integer> addToCart(Integer productId, Integer quantity, java.util.Map<String, String> attributes) {
        Integer userId = UserContext.getUserId();
        if (userId == null) {
            return Result.failure("用户未登录");
        }
        // 数量校验：1<=quantity<=999，越界抛业务异常
        if (quantity == null || quantity < 1 || quantity > 999) {
            throw new RuntimeException("购买数量必须在1-999之间");
        }

        // 检查商品是否存在
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return Result.failure("商品不存在");
        }

        // 解析 SKU（attributes 空 -> 默认 SKU），库存按规格校验
        com.itcast.model.pojo.Sku sku = skuMapper.selectOne(new LambdaQueryWrapper<com.itcast.model.pojo.Sku>()
                .eq(com.itcast.model.pojo.Sku::getProductId, productId)
                .eq(com.itcast.model.pojo.Sku::getAttrsKey, com.itcast.util.SkuKeyUtil.build(attributes))
                .last("LIMIT 1"));
        if (sku == null) {
            return Result.failure("该规格不存在或已下架");
        }
        if (sku.getStock() == null || sku.getStock() < quantity) {
            return Result.failure("该规格库存不足");
        }

        // 购物车以 (userId, skuId) 为唯一行：同商品不同规格各占一行
        LambdaQueryWrapper<Cart> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Cart::getUserId, userId)
                   .eq(Cart::getSkuId, sku.getId());
        Cart existingCart = cartMapper.selectOne(queryWrapper);

        if (existingCart != null) {
            // 已存在，更新数量
            existingCart.setQuantity(existingCart.getQuantity() + quantity);
            existingCart.setUpdateTime(LocalDateTime.now());
            cartMapper.updateById(existingCart);
        } else {
            // 不存在，新增购物车记录
            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setProductId(productId);
            cart.setSkuId(sku.getId());
            cart.setAttributes(com.itcast.util.SkuKeyUtil.build(attributes));
            cart.setProductName(product.getName());
            cart.setProductImage(product.getImage());
            cart.setProductPrice(product.getPrice());
            cart.setQuantity(quantity);
            cart.setCreateTime(LocalDateTime.now());
            cart.setUpdateTime(LocalDateTime.now());
            cartMapper.insert(cart);
        }

        return Result.success(sku.getId());
    }

    @Override
    public Result<List<Cart>> getCartList() {
        Integer userId = UserContext.getUserId();
        if (userId == null) {
            return Result.failure("用户未登录");
        }

        LambdaQueryWrapper<Cart> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Cart::getUserId, userId)
                   .orderByDesc(Cart::getCreateTime);
        List<Cart> cartList = cartMapper.selectList(queryWrapper);

        return Result.success(cartList);
    }

    @Override
    @Transactional
    public Result<Void> updateCartQuantity(Integer cartId, Integer quantity) {
        Integer userId = UserContext.getUserId();
        if (userId == null) {
            return Result.failure("用户未登录");
        }

        Cart cart = cartMapper.selectById(cartId);
        if (cart == null || !cart.getUserId().equals(userId)) {
            return Result.failure("购物车记录不存在");
        }

        // 检查库存
        Product product = productMapper.selectById(cart.getProductId());
        if (product == null || product.getStock() < quantity) {
            return Result.failure("库存不足");
        }

        cart.setQuantity(quantity);
        cart.setUpdateTime(LocalDateTime.now());
        cartMapper.updateById(cart);

        return Result.success(null);
    }

    @Override
    @Transactional
    public Result<Void> removeFromCart(Integer cartId) {
        Integer userId = UserContext.getUserId();
        if (userId == null) {
            return Result.failure("用户未登录");
        }

        Cart cart = cartMapper.selectById(cartId);
        if (cart == null || !cart.getUserId().equals(userId)) {
            return Result.failure("购物车记录不存在");
        }

        cartMapper.deleteById(cartId);
        return Result.success(null);
    }

    @Override
    @Transactional
    public Result<Void> clearCart() {
        Integer userId = UserContext.getUserId();
        if (userId == null) {
            return Result.failure("用户未登录");
        }

        LambdaQueryWrapper<Cart> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Cart::getUserId, userId);
        cartMapper.delete(queryWrapper);

        return Result.success(null);
    }
}
