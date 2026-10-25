package com.itcast.controller;

import com.itcast.context.UserContext;
import com.itcast.model.dto.BuyDto;
import com.itcast.model.dto.ProductDto;
import com.itcast.model.pojo.Product;
import com.itcast.model.vo.ProductVo;
import com.itcast.result.Result;
import com.itcast.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product")
public class ProductController {

    /**
     * 内部接口令牌校验（见 InternalTokenGuard：默认令牌时 helper 接口熔断）
     */
    @org.springframework.beans.factory.annotation.Autowired
    private com.itcast.config.InternalTokenGuard internalTokenGuard;

    @Autowired
    private ProductService productService;

    @GetMapping("/getProductList")
    public Result<List<Product>> getProductList(@RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer pageSize) {
        return productService.getProductList(page, pageSize);
    }

    /**
     * 推荐商品（前端商城页调用）：取库存>0 的商品按 id 倒序前 10 个
     */
    @GetMapping("/recommend")
    public Result<List<Product>> recommend() {
        return productService.recommend(10);
    }

    @GetMapping("/getProduct/{productId}")
    public Result<ProductVo> getProduct(@PathVariable Integer productId) {
        return productService.getProduct(productId);
    }

    @GetMapping("/getProductsByShop/{productId}")
    public Result<List<Product>> getProductsByShop(@PathVariable Integer productId) {
        return productService.getProductByShop(productId);
    }

    @PostMapping("/postProduct")
    public Result<Void> postProduct(@RequestBody ProductDto productDto) {
        return productService.postProduct(productDto);
    }

    @PutMapping("/updateProduct")
    public Result<Void> updateProduct(@RequestBody ProductDto productDto) {
        return productService.updateProduct(productDto);
    }

    /**
     * 购物 Agent 多轮对话端点（支持 conversationId 维持上下文）
     * 请求体示例：{"conversationId":"abc123","message":"帮我买那个红色的"}
     */
    @PostMapping("/agent/chat")
    public Result<String> agentChat(@RequestBody AgentChatRequest request) {
        if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
            return Result.failure("message 不能为空");
        }
        return productService.agentChat(request.getConversationId(), request.getMessage());
    }

    public static class AgentChatRequest {
        private String conversationId;
        private String message;

        public String getConversationId() { return conversationId; }
        public void setConversationId(String conversationId) { this.conversationId = conversationId; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    /**
     * 库存回补接口（内部接口，供 order 服务订单超时关单后回补库存）
     * 使用内部令牌校验，令牌由配置项 internal.token 提供，调用方需保持一致
     */
    /**
     * 内部接口：按 id 查商品（供 order 服务下单链路使用，返回纯 Product 而非 ProductVo）
     */
    @GetMapping("/helper/getProductById/{productId}")
    public Result<Product> helperGetProductById(@RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
                                                @PathVariable Integer productId) {
        if (!internalTokenGuard.isValid(internalToken)) {
            return Result.failure("无效的内部令牌");
        }
        Product product = productService.selectById(productId);
        if (product == null) {
            return Result.failure("商品不存在");
        }
        return Result.success(product);
    }

    @PostMapping("/helper/restoreStock")
    public Result<Void> restoreStock(@RequestHeader(value = "X-Internal-Token", required = false) String internalToken,
                                     @RequestParam(value = "skuId", required = false) Integer skuId,
                                     @RequestParam(value = "productId", required = false) Integer productId,
                                     @RequestParam("quantity") Integer quantity) {
        if (!internalTokenGuard.isValid(internalToken)) {
            return Result.failure("无效的内部令牌");
        }
        // 历史订单无 skuId：按商品默认 SKU 兜底解析
        Integer target = skuId;
        if (target == null && productId != null) {
            com.itcast.model.pojo.Sku sku = productService.resolveSku(productId, null);
            if (sku == null) {
                return Result.failure("商品默认规格不存在");
            }
            target = sku.getId();
        }
        if (target == null) {
            return Result.failure("skuId与productId至少传一个");
        }
        return productService.restoreStock(target, quantity == null ? 0 : quantity);
    }

    /** 商品全部 SKU（详情页规格选择/库存展示用，公开接口） */
    @GetMapping("/getSkus/{productId}")
    public Result<java.util.List<com.itcast.model.pojo.Sku>> getSkus(@PathVariable Integer productId) {
        return productService.getSkus(productId);
    }
}
