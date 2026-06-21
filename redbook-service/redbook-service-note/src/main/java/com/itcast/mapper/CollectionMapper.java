package com.itcast.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itcast.model.pojo.Collection;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CollectionMapper extends BaseMapper<Collection> {
}
