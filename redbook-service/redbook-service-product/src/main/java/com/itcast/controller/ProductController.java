package com.itcast.controller;

import com.itcast.context.UserContext;
import com.itcast.model.dto.BuyDto;
import com.itcast.model.dto.ProductDto;
import com.itcast.model.pojo.Product;
import com.itcast.model.vo.ProductVo;
import com.itcast.result.Result;
import com.itcast.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    @GetMapping("/getProductList")
    public Result<List<Product>> getProductList() {
        return productService.getProductList();
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
        return productService.buyProduct(buyDto);
    }

    @PostMapping("/agent/buyProduct")
    public Result<String> agentBuyProduct(@RequestBody String message) {
        return productService.buyProduct(message);
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
}
