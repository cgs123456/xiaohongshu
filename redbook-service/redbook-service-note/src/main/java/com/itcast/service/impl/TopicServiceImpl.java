package com.itcast.service.impl;

import com.itcast.mapper.TopicMapper;
import com.itcast.model.pojo.Topic;
import com.itcast.result.Result;
import com.itcast.service.TopicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TopicServiceImpl implements TopicService {

    @Autowired
    private TopicMapper topicMapper;

    @Override
    public Result<List<Topic>> getTopics() {
        // 热度倒序取前 50：话题表无界增长且全量返回会拖垮接口（每个新话题都会建行）
        List<Topic> topics = topicMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Topic>()
                        .orderByDesc(Topic::getHot)
                        .last("LIMIT 50"));
        return Result.success(topics);
    }
}
