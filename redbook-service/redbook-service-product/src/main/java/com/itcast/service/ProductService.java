package com.itcast.service;

import com.itcast.model.dto.BuyDto;
import com.itcast.model.dto.ProductDto;
import com.itcast.model.pojo.Product;
import com.itcast.model.vo.ProductVo;
import com.itcast.result.Result;

import java.util.List;

public interface ProductService {
    Result<List<Product>> getProductList();

    Result<ProductVo> getProduct(Integer productId);

    Result<List<Product>> getProductByShop(Integer productId);

    Result<Void> postProduct(ProductDto productDto);

    Result<Void> updateProduct(ProductDto productDto);

    Result<Void> buyProduct(BuyDto buyDto);

    Result<String> buyProduct(String message);

    Product selectById(Integer productId);

    void updateById(Product product);
}
