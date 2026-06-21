package com.itcast.model.vo;

import com.itcast.model.pojo.CustomAttribute;
import com.itcast.model.pojo.Product;
import com.itcast.model.pojo.Shop;
import lombok.Data;

import java.util.List;

@Data
public class ProductVo {

    private Product product;

    /**
     * 店铺
     */
    private Shop shop;

    /**
     * 商品属性
     */
    private List<CustomAttribute> customAttributes;
}
