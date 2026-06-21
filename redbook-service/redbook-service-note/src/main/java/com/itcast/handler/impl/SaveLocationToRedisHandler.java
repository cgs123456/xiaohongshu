package com.itcast.handler.impl;

import com.itcast.constant.RedisConstant;
import com.itcast.handler.NoteHandler;
import com.itcast.model.dto.NoteDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(9)
@Slf4j
public class SaveLocationToRedisHandler extends NoteHandler {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
        RedisGeoCommands.GeoLocation<Object> geoLocation
                = new RedisGeoCommands.GeoLocation<>(noteDto.getId(), new Point(noteDto.getLongitude(), noteDto.getLatitude()));
        redisTemplate.opsForGeo().add(RedisConstant.NOTE_GEO, geoLocation);
        log.info("===已保存笔记位置信息，noteId: {}, longitude: {}, latitude: {}===", noteDto.getId(), noteDto.getLongitude(), noteDto.getLatitude());
    }

    @Override
    public void compensate(NoteDto noteDto) {
        // 补偿：从 Redis 删除已保存的位置信息
        if (noteDto.getId() != null) {
            try {
                redisTemplate.opsForGeo().remove(RedisConstant.NOTE_GEO, noteDto.getId());
                log.info("补偿操作：已从 Redis 删除位置信息，noteId: {}", noteDto.getId());
            } catch (Exception e) {
                log.error("补偿操作失败：从 Redis 删除位置信息 noteId: {} 时出错", noteDto.getId(), e);
            }
        }
    }
}
