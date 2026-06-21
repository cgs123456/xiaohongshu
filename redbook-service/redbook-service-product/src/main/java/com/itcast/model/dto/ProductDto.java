package com.itcast.model.dto;

import com.itcast.model.pojo.Product;
import com.itcast.model.pojo.ProductAttribute;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ProductDto extends Product {

    /**
     * 商品属性
     */
    private ProductAttribute productAttribute;
}
