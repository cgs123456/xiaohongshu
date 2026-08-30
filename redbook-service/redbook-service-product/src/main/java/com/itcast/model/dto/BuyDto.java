package com.itcast.model.dto;

import com.itcast.model.pojo.CustomAttribute;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 注意：该类经 RabbitMQ 默认 JDK 序列化在 product/order 两个服务间传输，
 * 两端的字段与 serialVersionUID 必须保持一致。
 */
@Data
public class BuyDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer productId;
    private List<CustomAttribute> selectAttributes;
    private Integer userId;
    /**
     * 购买数量（默认1，service层校验 1<=quantity<=999）
     */
    private Integer quantity = 1;
}
