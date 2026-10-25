package com.itcast.model.dto;

import com.itcast.model.pojo.Product;
import com.itcast.model.pojo.ProductAttribute;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ProductDto extends Product {

    /**
     * 商品属性（展示用，Mongo）
     */
    private ProductAttribute productAttribute;

    /**
     * SKU 列表（分规格库存）。为空时创建默认 SKU 承载商品级 stock
     */
    private java.util.List<SkuDto> skus;
}
