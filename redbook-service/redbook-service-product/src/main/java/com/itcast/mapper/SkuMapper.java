package com.itcast.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itcast.model.pojo.Sku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface SkuMapper extends BaseMapper<Sku> {

    /**
     * 原子条件扣减 SKU 库存（stock >= quantity 才扣），返回影响行数
     */
    @Update("UPDATE rb_sku SET stock = stock - #{quantity} WHERE id = #{skuId} AND stock >= #{quantity}")
    int deductStock(@Param("skuId") Integer skuId, @Param("quantity") int quantity);

    /**
     * 回补 SKU 库存（补偿/关单回滚）
     */
    @Update("UPDATE rb_sku SET stock = stock + #{quantity} WHERE id = #{skuId}")
    int restoreStock(@Param("skuId") Integer skuId, @Param("quantity") int quantity);

    /**
     * 扣减商品聚合库存（列表展示口径，与 SKU 扣减同事务保持一致）
     */
    @Update("UPDATE rb_product SET stock = stock - #{quantity} WHERE id = #{productId} AND stock >= #{quantity}")
    int deductProductStock(@Param("productId") Integer productId, @Param("quantity") int quantity);

    /**
     * 回补商品聚合库存
     */
    @Update("UPDATE rb_product SET stock = stock + #{quantity} WHERE id = #{productId}")
    int restoreProductStock(@Param("productId") Integer productId, @Param("quantity") int quantity);

    /** 商品的全部 SKU（发布后建 SKU / 详情展示用） */
    @Select("SELECT * FROM rb_sku WHERE product_id = #{productId}")
    List<Sku> selectByProductId(@Param("productId") Integer productId);
}
