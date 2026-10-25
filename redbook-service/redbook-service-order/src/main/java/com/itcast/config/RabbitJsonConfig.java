package com.itcast.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQ 消息 JSON 序列化：Spring AMQP 默认 JDK 序列化受反序列化白名单限制
 * （自定义类一律 SecurityException，消息会无限重投），且跨服务要求类路径逐字段一致。
 * 统一 Jackson JSON 后，生产/消费双方只需 JSON 结构与相同 FQCN 对齐。
 */
@Configuration
public class RabbitJsonConfig {

    @Bean
    public MessageConverter jacksonMessageConverter(org.springframework.http.converter.json.Jackson2ObjectMapperBuilder builder) {
        // Spring 定制 ObjectMapper（含 JacksonTimeFormatConfig 的 LocalDateTime 支持）：
        // 消息体含 java.time 字段（如 Attention MQ）时默认自建 ObjectMapper 会序列化失败
        return new Jackson2JsonMessageConverter(builder.build());
    }
}
