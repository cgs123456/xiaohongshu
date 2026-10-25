package com.itcast.model.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 商品规格（SKU）：库存的最小扣减口径。
 * attrs_key 为规格组合的规范串（label:value 按 label 排序，"; " 分隔，与订单明细快照同格式），
 * 无属性商品为 ""（默认 SKU）——全部库存操作统一走 SKU，不存在商品级直接扣减路径。
 */
@Data
@TableName("rb_sku")
public class Sku implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("product_id")
    private Integer productId;

    /** 规格组合串（"颜色:黑色; 尺码:XL"，无属性为 ""） */
    @TableField("attrs_key")
    private String attrsKey;

    @TableField("stock")
    private Integer stock;
}
