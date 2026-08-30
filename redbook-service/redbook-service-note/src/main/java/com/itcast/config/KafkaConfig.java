package com.itcast.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    /** 用户行为日志 topic（与 SendMessageAspect 统一使用） */
    public static final String USER_BEHAVIOR_LOG_TOPIC = "user-behavior-log";
    /** 接口耗时日志 topic */
    public static final String INTERFACE_TIME_LOG_TOPIC = "interface-time-log";
    /** 系统异常日志 topic */
    public static final String SYSTEM_EXCEPTION_LOG_TOPIC = "system-exception-log";

    @Bean
    public NewTopic userBehaviorLogTopic() {
        return TopicBuilder.name(USER_BEHAVIOR_LOG_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic interfaceTimeTopic() {
        return TopicBuilder.name(INTERFACE_TIME_LOG_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic systemExceptionTopic() {
        return TopicBuilder.name(SYSTEM_EXCEPTION_LOG_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
