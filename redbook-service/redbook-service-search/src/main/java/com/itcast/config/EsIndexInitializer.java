package com.itcast.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

/**
 * ES 索引引导：首次启动（空集群）时按显式映射创建 rb_note 索引。
 * 此前索引只靠笔记发布时隐式创建，全新环境搜索接口直接 NoSuchIndexException。
 */
@Component
@Slf4j
public class EsIndexInitializer implements ApplicationRunner {

    /** 向量维度与 EmbeddingService 默认模型（text-embedding-v3）一致 */
    private static final String MAPPING = """
            {
              "properties": {
                "title":       {"type": "text"},
                "content":     {"type": "text"},
                "summary":     {"type": "text"},
                "aiTags":      {"type": "keyword"},
                "type":        {"type": "keyword"},
                "time":        {"type": "keyword"},
                "userId":      {"type": "integer"},
                "like":        {"type": "integer"},
                "collection":  {"type": "integer"},
                "address":     {"type": "text"},
                "image":       {"type": "keyword", "index": false},
                "embedding":   {"type": "dense_vector", "dims": 1024, "index": true, "similarity": "cosine"}
              }
            }""";

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Override
    public void run(ApplicationArguments args) {
        try {
            IndexCoordinates index = IndexCoordinates.of("rb_note");
            var indexOps = elasticsearchOperations.indexOps(index);
            if (Boolean.FALSE.equals(indexOps.exists())) {
                indexOps.create();
                indexOps.putMapping(org.springframework.data.elasticsearch.core.document.Document.parse(MAPPING));
                log.info("已创建 ES 索引 rb_note（含 dense_vector 映射）");
            } else {
                log.info("ES 索引 rb_note 已存在，跳过创建");
            }
        } catch (Exception e) {
            // 索引创建失败不阻断服务启动（ES 暂不可用时搜索功能降级，重试由运维处理）
            log.error("ES 索引初始化失败", e);
        }
    }
}
