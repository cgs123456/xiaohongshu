package com.itcast.model.pojo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 商品自定义规格属性（label + 候选值）。BuyDto.selectAttributes 的元素类型，
 * 随消息 DTO 一起在 product/order 间传输，故与 DTO 一同下沉 redbook-common 单一副本。
 * 包名保持 com.itcast.model.pojo 不变，兼容既有消息。
 */
@Data
public class CustomAttribute implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 自定义标签
     */
    private String label;

    /**
     * 标签对应的数据
     */
    private List<String> value;
}
