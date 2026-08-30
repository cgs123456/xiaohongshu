package com.itcast.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itcast.model.pojo.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 原子条件扣减库存（影响行数为0表示库存不足）
     */
    @Update("UPDATE rb_product SET stock = stock - #{quantity} WHERE id = #{productId} AND stock >= #{quantity}")
    int deductStock(@Param("productId") Integer productId, @Param("quantity") Integer quantity);

    /**
     * 原子回补库存（订单超时取消/关单时使用）
     */
    @Update("UPDATE rb_product SET stock = stock + #{quantity} WHERE id = #{productId}")
    int restoreStock(@Param("productId") Integer productId, @Param("quantity") Integer quantity);
}
