package com.itcast.controller;

import com.itcast.model.pojo.Cart;
import com.itcast.result.Result;
import com.itcast.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    @org.springframework.beans.factory.annotation.Autowired
    private com.itcast.service.CheckoutService checkoutService;

    /**
     * 购物车结算下单（多商品一单）：收货信息 + 可选优惠券 + 选中商品（空为全部）
     */
    @PostMapping("/checkout")
    public Result<String> checkout(@RequestBody com.itcast.model.dto.CheckoutDto checkoutDto) {
        return checkoutService.checkout(checkoutDto);
    }

    /**
     * 添加商品到购物车
     */
    @PostMapping("/addToCart")
    public Result<Void> addToCart(@RequestParam Integer productId,
                                  @RequestParam(defaultValue = "1") Integer quantity) {
        if (productId == null) {
            return Result.failure("商品ID不能为空");
        }
        return cartService.addToCart(productId, quantity);
    }

    /**
     * 获取用户购物车列表
     */
    @GetMapping("/getCartList")
    public Result<List<Cart>> getCartList() {
        return cartService.getCartList();
    }

    /**
     * 更新购物车商品数量
     */
    @PutMapping("/updateQuantity")
    public Result<Void> updateQuantity(@RequestParam Integer cartId,
                                       @RequestParam Integer quantity) {
        if (cartId == null || quantity == null || quantity < 1) {
            return Result.failure("参数不合法");
        }
        return cartService.updateCartQuantity(cartId, quantity);
    }

    /**
     * 删除购物车商品
     */
    @DeleteMapping("/remove/{cartId}")
    public Result<Void> removeFromCart(@PathVariable Integer cartId) {
        return cartService.removeFromCart(cartId);
    }

    /**
     * 清空购物车
     */
    @DeleteMapping("/clear")
    public Result<Void> clearCart() {
        return cartService.clearCart();
    }
}
