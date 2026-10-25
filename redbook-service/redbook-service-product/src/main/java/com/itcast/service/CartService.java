package com.itcast.service;

import com.itcast.model.pojo.Cart;
import com.itcast.result.Result;

import java.util.List;

public interface CartService {
    /**
     * 添加商品到购物车
     */
    /** 加购（attributes 为空取默认 SKU）；返回 data=skuId 供结算按规格提交 */
    Result<Integer> addToCart(Integer productId, Integer quantity, java.util.Map<String, String> attributes);

    /**
     * 获取用户购物车列表
     */
    Result<List<Cart>> getCartList();

    /**
     * 更新购物车商品数量
     */
    Result<Void> updateCartQuantity(Integer cartId, Integer quantity);

    /**
     * 删除购物车商品
     */
    Result<Void> removeFromCart(Integer cartId);

    /**
     * 清空购物车
     */
    Result<Void> clearCart();
}
