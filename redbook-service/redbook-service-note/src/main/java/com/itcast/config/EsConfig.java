package com.itcast.config;

import com.itcast.model.pojo.NoteEs;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

/**
 * elasticsearch配置类
 * 使用 Spring Data Elasticsearch 自动配置，无需手动创建 RestHighLevelClient
 * 配置在 application.yml 中通过 spring.data.elasticsearch.uris 指定
 */
@Configuration
@Slf4j
public class EsConfig {

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    /**
     * 启动时检查并创建 rb_note 索引（含 dense_vector mapping）。
     * 索引不存在时按 @Mapping 注解指定的 JSON 创建；
     * 索引已存在则跳过（需手动 DELETE /rb_note 后重启才能应用新 mapping）。
     * 异常不阻塞服务启动（ES 不可用时降级）。
     */
    @PostConstruct
    public void initNoteIndex() {
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(NoteEs.class);
            if (!indexOps.exists()) {
                indexOps.createWithMapping();
                log.info("ES 索引 rb_note 创建成功（含 dense_vector mapping）");
            } else {
                log.info("ES 索引 rb_note 已存在，跳过 mapping 初始化");
            }
        } catch (Exception e) {
            log.warn("ES 索引 rb_note 初始化异常（服务仍可启动）: {}", e.getMessage());
        }
    }
}
