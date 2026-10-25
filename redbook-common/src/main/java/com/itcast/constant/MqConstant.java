package com.itcast.constant;

public class MqConstant {
    public final static String MESSAGE_NOTICE_EXCHANGE = "message.notice.exchange";
    public final static String LOGIN_NOTICE_QUEUE = "login.notice.queue";
    public final static String LIKE_NOTICE_QUEUE = "like.notice.queue";
    public final static String COLLECTION_NOTICE_QUEUE = "collection.notice.queue";
    public final static String ATTENTION_NOTICE_QUEUE = "attention.notice.queue";
    public final static String LOGIN_KEY = "login.key";
    public final static String LIKE_KEY = "like.key";
    public final static String COLLECTION_KEY = "collection.key";
    public final static String ATTENTION_KEY = "attention.key";

    public static final String SAVE_ORDER_EXCHANGE = "save.order.exchange";
    public static final String SAVE_ORDER_QUEUE = "save.order.queue";

    public static final String UPDATE_PRODUCT_STOCK_EXCHANGE = "update.product.stock.exchange";
    public static final String UPDATE_PRODUCT_STOCK_QUEUE = "update.product.stock.queue";
    /** 库存消息死信（重试超限保留待人工重放，product 服务声明/消费） */
    public static final String UPDATE_PRODUCT_STOCK_DEAD_EXCHANGE = "update.product.stock.dead.exchange";
    public static final String UPDATE_PRODUCT_STOCK_DEAD_QUEUE = "update.product.stock.dead.queue";

    /** 购物车结算下单（多商品一单，含快照/地址/优惠） */
    public static final String CHECKOUT_ORDER_EXCHANGE = "checkout.order.exchange";
    public static final String CHECKOUT_ORDER_QUEUE = "checkout.order.queue";
}
