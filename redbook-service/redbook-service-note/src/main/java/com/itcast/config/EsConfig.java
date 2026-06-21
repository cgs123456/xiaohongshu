package com.itcast.config;

import org.springframework.context.annotation.Configuration;

/**
 * elasticsearch配置类
 * 使用 Spring Data Elasticsearch 自动配置，无需手动创建 RestHighLevelClient
 * 配置在 application.yml 中通过 spring.data.elasticsearch.uris 指定
 */
@Configuration
public class EsConfig {
    // Spring Data Elasticsearch 会自动配置 ElasticsearchOperations 和 ElasticsearchRestTemplate
    // 无需手动创建 RestHighLevelClient
}
