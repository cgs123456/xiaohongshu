package com.itcast.controller;

import com.itcast.model.pojo.Topic;
import com.itcast.result.Result;
import com.itcast.service.TopicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/note")
public class TopicController {

    @Autowired
    private TopicService topicService;

    @GetMapping("/getTopics")
    public Result<List<Topic>> getTopics() {
        return topicService.getTopics();
    }
}
