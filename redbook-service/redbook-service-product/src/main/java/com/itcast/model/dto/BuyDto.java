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

    /**
     * 仅在 product 服务内部 UPDATE_PRODUCT_STOCK 队列使用：
     * true 表示这条消息是"回补库存"补偿（MQ 发送失败时补发），消费者走 restoreStock 分支。
     * 不会发给 order 服务（跨服务方向新增字段是 JDK 序列化的兼容演进，接收端忽略未知字段）。
     */
    private Boolean restore = false;
}
