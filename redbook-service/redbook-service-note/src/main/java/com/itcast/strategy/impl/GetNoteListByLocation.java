package com.itcast.strategy.impl;

import com.itcast.component.UserBatchLoader;
import com.itcast.constant.RedisConstant;
import com.itcast.mapper.NoteMapper;
import com.itcast.model.pojo.Note;
import com.itcast.model.pojo.User;
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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@Slf4j
public class GetNoteListByLocation implements GetNotesStrategy {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${geo.distance}")
    private String geoDistance;

    @Autowired
    private UserBatchLoader userBatchLoader;

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
        // 2.构造请求参数（limit 按需截断，避免城市级密度下 5km 内数千条笔记全量组装）
        RedisGeoCommands.GeoRadiusCommandArgs geoArgs = RedisGeoCommands.GeoRadiusCommandArgs
                .newGeoRadiusArgs()
                .includeDistance()
                .includeCoordinates()
                .limit(200);
        // 3.发起请求
        GeoResults<RedisGeoCommands.GeoLocation<Object>> radius
                = redisTemplate.opsForGeo().radius(RedisConstant.NOTE_GEO, circle, geoArgs);
        // 4.定义结果
        List<NoteVo> resultList = new ArrayList<>();
        if (radius != null) {
            // 5.收集笔记id，一次性批量查询，消除 N+1
            List<Integer> noteIds = new ArrayList<>();
            List<Distance> distances = new ArrayList<>();
            for (GeoResult<RedisGeoCommands.GeoLocation<Object>> geoResult : radius) {
                Object name = geoResult.getContent().getName();
                // geo member 反序列化可能是 Integer 或 Long，两者都接受
                if (name instanceof Integer) {
                    noteIds.add((Integer) name);
                    distances.add(geoResult.getDistance());
                } else if (name instanceof Long) {
                    noteIds.add(((Long) name).intValue());
                    distances.add(geoResult.getDistance());
                }
            }
            if (noteIds.isEmpty()) {
                return Result.success(resultList);
            }
            Map<Long, Note> noteMap = noteMapper.selectBatchIds(noteIds).stream()
                    .collect(Collectors.toMap(Note::getId, n -> n, (a, b) -> a));
            // 6.批量查询用户
            List<Integer> userIds = noteMap.values().stream().map(Note::getUserId)
                    .filter(Objects::nonNull).distinct().collect(Collectors.toList());
            Map<Integer, User> userMap = userBatchLoader.batchGetUsers(userIds);
            // 7.组装 VO，笔记不存在的跳过该条，绝不让整个列表 500
            for (int i = 0; i < noteIds.size(); i++) {
                Note note = noteMap.get(noteIds.get(i).longValue());
                if (note == null) {
                    continue;
                }
                NoteVo noteVo = new NoteVo();
                noteVo.setNote(note);
                noteVo.setUser(userMap.get(note.getUserId()));
                // 处理距离
                noteVo.setDistance(dealDistance(distances.get(i).getValue()));
                resultList.add(noteVo);
            }
            log.info("同城笔记查询完成，本页 {} 条", resultList.size());
        }
        // 8.分页（geo 结果按距离排序，直接在内存中截取）
        int page = page(context);
        int pageSize = pageSize(context);
        int fromIndex = (page - 1) * pageSize;
        if (fromIndex >= resultList.size()) {
            return Result.success(new ArrayList<>());
        }
        int toIndex = Math.min(fromIndex + pageSize, resultList.size());
        return Result.success(resultList.subList(fromIndex, toIndex));
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

    private int page(NoteStrategyContext context) {
        if (context == null || context.getPage() == null || context.getPage() < 1) {
            return 1;
        }
        return context.getPage();
    }

    private int pageSize(NoteStrategyContext context) {
        int size = (context == null || context.getPageSize() == null) ? 20 : context.getPageSize();
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
