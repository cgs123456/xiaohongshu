package com.itcast.strategy.impl;

import com.itcast.client.UserClient;
import com.itcast.constant.ExceptionConstant;
import com.itcast.constant.RedisConstant;
import com.itcast.exception.NoteNoExistException;
import com.itcast.mapper.NoteMapper;
import com.itcast.model.pojo.Note;
import com.itcast.model.vo.NoteVo;
import com.itcast.result.Result;
import com.itcast.strategy.GetNotesStrategy;
import com.itcast.strategy.NoteStrategyContext;
import com.itcast.strategy.NoteStrategyType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class GetNoteListByLocation implements GetNotesStrategy {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${geo.distance}")
    private String geoDistance;

    @Autowired
    private UserClient userClient;

    @Autowired
    private NoteMapper noteMapper;

    @Override
    public NoteStrategyType getStrategyType() {
        return NoteStrategyType.BY_LOCATION;
    }

    @Override
    public Result<List<NoteVo>> getNotes(NoteStrategyContext context) {
        // 1.构造圆
        Point point = new Point(Double.parseDouble(context.getLongitude()), Double.parseDouble(context.getLatitude()));
        Distance distance = new Distance(
                Double.parseDouble(geoDistance), RedisGeoCommands.DistanceUnit.KILOMETERS);
        Circle circle = new Circle(point, distance);
        // 2.构造请求参数
        RedisGeoCommands.GeoRadiusCommandArgs geoArgs = RedisGeoCommands.GeoRadiusCommandArgs
                .newGeoRadiusArgs()
                .includeDistance()
                .includeCoordinates();
        // 3.发起请求
        GeoResults<RedisGeoCommands.GeoLocation<Object>> radius
                = redisTemplate.opsForGeo().radius(RedisConstant.NOTE_GEO, circle, geoArgs);
        // 4.定义结果
        List<NoteVo> resultList = new ArrayList<>();
        if (radius != null) {
            // 5.遍历
            for (GeoResult<RedisGeoCommands.GeoLocation<Object>> geoResult : radius) {
                // 5.1 获取内容
                RedisGeoCommands.GeoLocation<Object> geoResultContent = geoResult.getContent();
                // 5.2 获取距离
                Distance geoDistance = geoResult.getDistance();
                // 5.3 获取笔记
                Integer noteId = (Integer) geoResultContent.getName();
                Note note = noteMapper.selectById(noteId);
                if (note == null) {
                    throw new NoteNoExistException(ExceptionConstant.NOTE_NO_EXIST);
                }
                NoteVo noteVo = new NoteVo();
                noteVo.setNote(note);
                noteVo.setUser(userClient.getUserById(note.getUserId()).getData());
                // 5.4 处理距离
                String dealDistance = this.dealDistance(geoDistance.getValue());
                noteVo.setDistance(dealDistance);
                resultList.add(noteVo);
            }
            log.info("最终的结果是：{}", resultList);
        }
        return Result.success(resultList);
    }

    /**
     * 处理距离
     * @param distance
     * @return
     */
    public String dealDistance(Double distance) {
        distance = distance * 1000; // 将距离转换为米
        String dealDistance = "";
        if (distance <= 1000) {
            dealDistance = String.format("%.1f米", distance);
        } else {
            dealDistance = String.format("%.1fkm", distance / 1000);
        }
        return dealDistance;
    }
}
