package com.itcast.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic userBehaviorLogTopic() {
        return TopicBuilder.name("user-behavior-log")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic interfaceTimeTopic() {
        return TopicBuilder.name("interface-time-log")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic systemExceptionTopic() {
        return TopicBuilder.name("system-exception-log")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
