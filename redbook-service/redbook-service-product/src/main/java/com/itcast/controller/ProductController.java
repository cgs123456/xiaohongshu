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
     * 内部接口令牌（order 服务调用 /helper/restoreStock 时需携带一致的令牌）
     */
    @Value("${internal.token:change-me-internal-token}")
    private String internalToken;

    @Autowired
    private ProductService productService;

    @GetMapping("/getProductList")
    public Result<List<Product>> getProductList(@RequestParam(required = false) Integer page,
                                                @RequestParam(required = false) Integer pageSize) {
        return productService.getProductList(page, pageSize);
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

    @PostMapping("/buyProduct")
    public Result<Void> buyProduct(@RequestBody BuyDto buyDto) {
        if (buyDto.getProductId() == null) {
            return Result.failure("商品ID不能为空");
        }
        // 强制使用登录态中的userId，防止客户端伪造
        buyDto.setUserId(UserContext.getUserId());
        return productService.buyProduct(buyDto);
    }

    @PostMapping("/agent/buyProduct")
    public Result<String> agentBuyProduct(@RequestBody String message) {
        return productService.buyProduct(message);
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

    @GetMapping("/helper/buyProduct")
    public Result<Void> helperBuyProduct(@RequestParam Integer productId) {
        if (productId == null) {
            return Result.failure("商品ID不能为空");
        }
        BuyDto buyDto = new BuyDto();
        buyDto.setProductId(productId);
        buyDto.setUserId(UserContext.getUserId());
        return productService.buyProduct(buyDto);
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
        if (internalToken == null || !internalToken.equals(this.internalToken)) {
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
                                     @RequestParam("productId") Integer productId,
                                     @RequestParam("quantity") Integer quantity) {
        if (productId == null || quantity == null || quantity <= 0) {
            return Result.failure("参数错误");
        }
        if (internalToken == null || !internalToken.equals(this.internalToken)) {
            return Result.failure("无效的内部令牌");
        }
        return productService.restoreStock(productId, quantity);
    }
}
