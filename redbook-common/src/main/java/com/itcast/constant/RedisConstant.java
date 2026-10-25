package com.itcast.constant;

public class RedisConstant {
    public static final String PHONE_CODE = "redbook:user:phone_code:";
    public static final String ATTENTION_CACHE = "redbook:user:attention_cache:";
    public static final String NOTE_GEO = "redbook:note:note_geo:";
    public static final String NOTE_DETAIL_CACHE = "redbook:note:note_detail_cache:";
    public static final String NOTE_SCORE = "redbook:note:note_score:";
    public static final String LIKE_SET_CACHE = "redbook:note:like_set_cache:";
    public static final String COLLECTION_SET_CACHE = "redbook:note:collection_set_cache:";
    public static final String PRODUCT_STOCK_CACHE = "redbook:product:stock:cache:";
    /** SKU 级库存缓存 key（库存分规格后的唯一扣减口径），完整 key: 前缀 + skuId */
    public static final String PRODUCT_SKU_STOCK_CACHE = "redbook:product:sku_stock:cache:";
    /** 关注流收件箱（推拉结合推侧），完整 key: 前缀 + userId，List 结构存 noteId */
    public static final String FEED_INBOX = "redbook:note:feed_inbox:";

    // ===== Shopping Agent 相关 =====
    /** 多轮对话存储前缀，完整 key: agent:conversation:{conversationId} */
    public final static String AGENT_CONVERSATION = "agent:conversation:";
    /** Few-Shot 成功样本列表 key */
    public final static String AGENT_FEWSHOT_SUCCESS = "agent:fewshot:success";
    /** Few-Shot 失败样本列表 key */
    public final static String AGENT_FEWSHOT_FAILURE = "agent:fewshot:failure";
    /** 格式正确率计数器（hash: total / success） */
    public final static String AGENT_FORMAT_STATS = "agent:format:stats";
}
