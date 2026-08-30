package com.itcast.client;

import com.itcast.model.pojo.Product;
import com.itcast.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@Component
@FeignClient("redbook-service-product")
public interface ProductClient {

    /**
     * 内部接口：按 id 查商品（product 侧 /getProduct 返回的是 ProductVo，直接反序列化会得到全 null 字段）
     */
    @GetMapping("/product/helper/getProductById/{productId}")
    Result<Product> getProductById(@RequestHeader("X-Internal-Token") String internalToken,
                                   @PathVariable Integer productId);

    @PutMapping("/product/updateProduct")
    Result<Void> updateProduct(@RequestBody Product product);

    /**
     * 库存回补（订单超时关单后调用，需携带与 product 服务一致的内部令牌）
     */
    @PostMapping("/product/helper/restoreStock")
    Result<Void> restoreStock(@RequestHeader("X-Internal-Token") String internalToken,
                              @RequestParam("productId") Integer productId,
                              @RequestParam("quantity") Integer quantity);
}
