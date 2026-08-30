package com.itcast.service.impl;

import com.itcast.constant.RedisConstant;
import com.itcast.result.Result;
import com.itcast.service.HotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class HotServiceImpl implements HotService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Result<List<Map<String, Object>>> getHotList() {
        // 1.一次性获取热度前10名（带分数），避免逐条查询分数
        Set<ZSetOperations.TypedTuple<Object>> hots =
                redisTemplate.opsForZSet().reverseRangeWithScores(getHotKey(), 0, 9);
        if (CollectionUtils.isEmpty(hots)) {
            return Result.success(null);
        }
        List<Map<String, Object>> hotList = hots.stream().map(tuple -> {
            Map<String, Object> map = new HashMap<>();
            map.put("key", tuple.getValue());
            map.put("score", tuple.getScore());
            return map;
        }).collect(Collectors.toList());
        log.info("热搜前十名是:{}", hotList);
        return Result.success(hotList);
    }

    /**
     * 热搜按天分 key：原前缀 + yyyyMMdd（与 SearchServiceImpl 写入侧保持一致，TTL 7天）
     */
    private String getHotKey() {
        return RedisConstant.NOTE_SCORE + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    }
}
