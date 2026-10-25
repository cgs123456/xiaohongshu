package com.itcast.model.dto;

import lombok.Data;

import java.util.Map;

/**
 * 发布商品时声明的 SKU（规格 + 该规格独立库存）。
 * 不传 skus 时系统自动创建默认 SKU（attrs_key=""，承载商品级库存）。
 */
@Data
public class SkuDto {

    /** 规格选择（label -> 值），如 {"颜色":"黑","尺码":"XL"}；空 map 等同默认 SKU */
    private Map<String, String> attributes;

    /** 该规格库存（>=0） */
    private Integer stock;
}
