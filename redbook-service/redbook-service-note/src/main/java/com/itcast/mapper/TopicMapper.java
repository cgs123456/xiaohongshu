package com.itcast.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itcast.model.pojo.Topic;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TopicMapper extends BaseMapper<Topic> {

    /** 原子自增热度（替代读改写，避免并发丢失更新） */
    @Update("UPDATE rb_topic SET hot = hot + 1 WHERE id = #{topicId}")
    int incrementHot(@Param("topicId") Integer topicId);

    /** 原子回退热度（补偿用，不为负保护） */
    @Update("UPDATE rb_topic SET hot = hot - 1 WHERE id = #{topicId} AND hot > 0")
    int decrementHot(@Param("topicId") Integer topicId);
}
