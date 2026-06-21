package com.itcast.service.impl;

import com.itcast.client.UserClient;
import com.itcast.constant.RedisConstant;
import com.itcast.mapper.HistoryMapper;
import com.itcast.model.pojo.History;
import com.itcast.model.pojo.Note;
import com.itcast.model.vo.NoteVo;
import com.itcast.result.Result;
import com.itcast.service.SearchService;
import com.itcast.context.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class SearchServiceImpl implements SearchService {

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private UserClient userClient;

    @Autowired
    private HistoryMapper historyMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Result<List<NoteVo>> search(String key) {
        // 1.构造查询 - 使用新的 NativeQuery API
        Query query = NativeQuery.builder()
                .withQuery(q -> q.multiMatch(m -> m
                        .query(key)
                        .fields("title", "content")
                ))
                .build();

        // 2.执行查询
        SearchHits<Note> searchHits = elasticsearchOperations.search(
                query,
                Note.class,
                IndexCoordinates.of("rb_note")
        );

        // 3.设置vo
        List<NoteVo> noteVos = new ArrayList<>();
        for (SearchHit<Note> hit : searchHits.getSearchHits()) {
            Note note = hit.getContent();
            // 4.2 设置vo
            NoteVo noteVo = new NoteVo();
            BeanUtils.copyProperties(note, noteVo);
            noteVo.setUser(userClient.getUserById(note.getUserId()).getData());
            noteVos.add(noteVo);
        }

        // 5.保存搜索记录（去重）
        try {
            Integer currentUserId = UserContext.getUserId();
            // 先查询是否已存在
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<History> queryWrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            queryWrapper.eq(History::getUserId, currentUserId)
                       .eq(History::getHistory, key);
            History existingHistory = historyMapper.selectOne(queryWrapper);

            if (existingHistory != null) {
                // 已存在，更新记录
                log.info("搜索记录已存在，更新: userId={}, key={}", currentUserId, key);
            } else {
                // 不存在，插入新记录
                History history = new History();
                history.setHistory(key);
                history.setUserId(currentUserId);
                historyMapper.insert(history);
            }
        } catch (Exception e) {
            log.info("用户搜索记录处理异常: {}", e.getMessage());
        }

        // 6.保存热度
        redisTemplate.opsForZSet().incrementScore(RedisConstant.NOTE_SCORE, key, 1);
        return Result.success(noteVos);
    }
}
