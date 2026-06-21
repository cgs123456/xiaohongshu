package com.itcast.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itcast.model.pojo.NoteTopic;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NoteTopicMapper extends BaseMapper<NoteTopic> {
}
