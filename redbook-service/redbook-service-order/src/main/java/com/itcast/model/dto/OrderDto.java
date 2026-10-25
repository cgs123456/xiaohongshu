package com.itcast.model.dto;

import com.itcast.model.pojo.CustomAttribute;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderDto implements Serializable {

    /**
     * 产品id
     */
    private Integer productId;

    /**
     * 规格id（库存扣减口径；直购消息发前已在 product 侧解析，永不为空）
     */
    private Integer skuId;

    /**
     * 产品价格
     */
    private BigDecimal price;

    /**
     * 选择的属性
     */
    private List<CustomAttribute> selectAttributes;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 购买数量（默认1）
     */
    private Integer quantity;

    /**
     * 消息唯一标识（用于幂等性控制）
     */
    private String messageId;
}
