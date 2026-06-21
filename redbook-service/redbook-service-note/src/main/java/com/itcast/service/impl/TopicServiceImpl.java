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
        List<Topic> topics = topicMapper.selectList(null);
        return Result.success(topics);
    }
}
