package com.itcast.handler.impl;

import com.itcast.handler.NoteHandler;
import com.itcast.model.dto.NoteDto;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Order(5)
@Slf4j
public class FilterTitleHandler extends NoteHandler {

    /**
     * 敏感词
     */
    @Data
    public static class SensitiveWord {
        private String sensitiveWord;
    }

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    private static final String SENSITIVE_WORD_INDEX = "rb_sensitive_word";

    /**
     * ES 查询条数上限（matchAll 不设置 size 默认只返回 10 条）
     */
    private static final int ES_QUERY_SIZE = 1000;

    /**
     * 敏感词缓存刷新间隔：5 分钟
     */
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;

    /**
     * 敏感词静态内存缓存（volatile 保证多线程可见性）
     */
    private static volatile HashSet<String> sensitiveWordCache = new HashSet<>();

    /**
     * 上次加载时间戳（volatile）
     */
    private static volatile long lastLoadTime = 0L;

    @Override
    public void handle(NoteDto noteDto) {
        // 同时过滤标题和内容
        if (noteDto.getTitle() != null) {
            noteDto.setTitle(sensitiveWordFilter(noteDto.getTitle()));
        }
        if (noteDto.getContent() != null) {
            noteDto.setContent(sensitiveWordFilter(noteDto.getContent()));
        }
    }

    /**
     * 敏感词过滤
     * @param text
     * @return
     */
    public String sensitiveWordFilter(String text) {
        HashSet<String> sensitiveWords = getSensitiveWords();
        for (String sensitiveWord : sensitiveWords) {
            if (sensitiveWord == null || sensitiveWord.isEmpty()) {
                continue;
            }
            // Pattern.quote 防止敏感词中的正则元字符导致异常
            text = text.replaceAll(Pattern.quote(sensitiveWord), "**");
        }
        return text;
    }

    /**
     * 获取敏感词集合：首次加载 + 5 分钟 TTL 刷新；异常时降级用上次缓存
     */
    private HashSet<String> getSensitiveWords() {
        long now = System.currentTimeMillis();
        // 缓存未过期直接返回，避免每次发布都查 ES
        if (!sensitiveWordCache.isEmpty() && now - lastLoadTime < CACHE_TTL_MS) {
            return sensitiveWordCache;
        }
        try {
            // 1.构建搜索查询 - 使用 matchAll 获取所有敏感词，并设置 size
            Query searchQuery = NativeQuery.builder()
                    .withQuery(q -> q.matchAll(m -> m))
                    .withMaxResults(ES_QUERY_SIZE)
                    .build();

            // 2.执行搜索请求
            List<SearchHit<SensitiveWord>> searchHits = elasticsearchOperations.search(
                    searchQuery,
                    SensitiveWord.class,
                    IndexCoordinates.of(SENSITIVE_WORD_INDEX)
            ).getSearchHits();

            // 3.提取敏感词列表
            HashSet<String> words = searchHits.stream()
                    .map(hit -> hit.getContent().getSensitiveWord())
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(HashSet::new));

            // 4.刷新缓存（空结果也刷新时间戳，避免每次都打 ES）
            if (!words.isEmpty()) {
                sensitiveWordCache = words;
            }
            lastLoadTime = now;
            return sensitiveWordCache;
        } catch (Exception e) {
            // 异常时降级用上次缓存
            log.error("加载敏感词失败，降级使用上次缓存，当前缓存数量: {}", sensitiveWordCache.size(), e);
            return sensitiveWordCache;
        }
    }
}
