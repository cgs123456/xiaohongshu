package com.itcast.service;

import com.itcast.model.pojo.Topic;
import com.itcast.result.Result;

import java.util.List;

public interface TopicService {
    Result<List<Topic>> getTopics();
}
