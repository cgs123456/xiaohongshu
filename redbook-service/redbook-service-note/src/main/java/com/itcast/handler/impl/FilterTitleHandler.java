package com.itcast.handler.impl;

import com.itcast.handler.NoteHandler;
import com.itcast.model.dto.NoteDto;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Order(4)
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

    @Override
    public void handle(NoteDto noteDto) throws IOException, InterruptedException {
        String newTitle = sensitiveWordFilter(noteDto.getTitle());
        noteDto.setTitle(newTitle);
    }

    /**
     * 敏感词过滤
     * @param text
     * @return
     */
    public String sensitiveWordFilter(String text) {
        try {
            // 1.构建搜索查询 - 使用 matchAll 获取所有敏感词
            Query searchQuery = NativeQuery.builder()
                    .withQuery(q -> q.matchAll(m -> m))
                    .build();

            // 2.执行搜索请求
            List<SearchHit<SensitiveWord>> searchHits = elasticsearchOperations.search(
                    searchQuery, 
                    SensitiveWord.class, 
                    IndexCoordinates.of(SENSITIVE_WORD_INDEX)
            ).getSearchHits();

            // 3.提取敏感词列表
            List<String> sensitiveWords = searchHits.stream()
                    .map(hit -> hit.getContent().getSensitiveWord())
                    .collect(Collectors.toList());

            // 4.过滤敏感词
            for (String sensitiveWord : sensitiveWords) {
                text = text.replaceAll(sensitiveWord, "**");
            }
            return text;
        } catch (Exception e) {
            return text; // 如果搜索失败，返回原文
        }
    }
}
