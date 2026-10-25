package com.itcast.model.dto;

import com.itcast.model.pojo.CustomAttribute;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 库存/下单消息体（product -> product / order，Jackson 序列化；
 * product 与 order 两端副本字段需保持一致，新增字段向后兼容）。
 */
@Data
public class BuyDto implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer productId;
    /** 规格id（库存分 SKU 后的扣减口径；无属性商品为默认 SKU，永不为空——发消息前必须解析） */
    private Integer skuId;
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
