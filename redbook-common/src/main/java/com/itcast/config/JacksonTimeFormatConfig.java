package com.itcast.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;

import java.time.format.DateTimeFormatter;

/**
 * 全局 java.time JSON 格式：保持与历史 VARCHAR 时间一致的
 * "yyyy-MM-dd HH:mm:ss"（LocalDate 为 "yyyy-MM-dd"），
 * 前端展示与跨服务 String 视图模型（comment/message/search 的副本类）无需任何改动。
 * 作用于各服务 Spring 管理的 ObjectMapper（REST + 注入点）——
 * 各服务 Application 扫描 com.itcast.*，本配置随 redbook-common 被全部服务装配。
 */
@Configuration
public class JacksonTimeFormatConfig {

    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @org.springframework.context.annotation.Bean
    public Jackson2ObjectMapperBuilderCustomizer timeFormatCustomizer() {
        return builder -> builder
                .serializers(
                        new LocalDateTimeSerializer(DATE_TIME),
                        new LocalDateSerializer(DATE))
                .deserializers(
                        new LocalDateTimeDeserializer(DATE_TIME),
                        new LocalDateDeserializer(DATE));
    }
}
