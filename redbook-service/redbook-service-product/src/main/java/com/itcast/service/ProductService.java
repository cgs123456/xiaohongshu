package com.itcast.service;

import com.itcast.model.dto.BuyDto;
import com.itcast.model.dto.ProductDto;
import com.itcast.model.pojo.Product;
import com.itcast.model.vo.ProductVo;
import com.itcast.result.Result;

import java.util.List;

public interface ProductService {

    /**
     * 推荐商品：库存>0，按 id 倒序取前 limit 个
     */
    Result<List<Product>> recommend(int limit);
    Result<List<Product>> getProductList();

    /**
     * 可选分页查询商品列表（page/pageSize 为空时返回全部，兼容现状）
     */
    Result<List<Product>> getProductList(Integer page, Integer pageSize);

    Result<ProductVo> getProduct(Integer productId);

    Result<List<Product>> getProductByShop(Integer productId);

    Result<Void> postProduct(ProductDto productDto);

    Result<Void> updateProduct(ProductDto productDto);

    Result<Void> buyProduct(BuyDto buyDto);

    Result<String> buyProduct(String message);

    /**
     * Agent 多轮对话（支持 conversationId 维持上下文）
     */
    Result<String> agentChat(String conversationId, String message);

    /**
     * 按关键词搜索商品（名称/描述/品牌模糊匹配）
     */
    Result<List<Product>> searchProducts(String keyword);

    Product selectById(Integer productId);

    void updateById(Product product);

    /**
     * 回补 SKU 库存（MySQL SKU 与商品聚合两级原子加 + Redis INCRBY，订单超时关单时由 order 服务调用）
     */
    Result<Void> restoreStock(Integer skuId, int quantity);

    /**
     * Redis Lua 原子扣减 SKU 库存（含缓存预热）。供 buyProduct 与购物车结算共用。
     * @return 扣减后剩余库存；-1 库存不足；-2 SKU 不存在
     */
    long deductRedisStock(Integer skuId, int quantity);

    /**
     * 回补 Redis SKU 库存（结算链路部分失败时补偿扣减）
     */
    void restoreRedisStock(Integer skuId, int quantity);

    /**
     * 解析 SKU：attributes 为 null/空取默认 SKU（attrs_key=""），否则按规范 key 精确匹配。
     * @return 匹配的 SKU；商品或规格不存在返回 null
     */
    com.itcast.model.pojo.Sku resolveSku(Integer productId, java.util.Map<String, String> attributes);

    /** 按 id 查 SKU（结算链路从购物车行解析规格） */
    com.itcast.model.pojo.Sku getSkuById(Integer skuId);

    /** 批量查商品（结算链路消除 N+1），返回 id -> Product */
    java.util.Map<Integer, com.itcast.model.pojo.Product> selectBatchIds(java.util.List<Integer> ids);

    /**
     * 商品的全部 SKU（详情页规格选择用）
     */
    Result<java.util.List<com.itcast.model.pojo.Sku>> getSkus(Integer productId);
}
