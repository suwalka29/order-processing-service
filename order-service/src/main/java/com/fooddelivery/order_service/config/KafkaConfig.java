package com.fooddelivery.order_service.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String ORDER_STATUS_TOPIC = "order-status-changed";

    @Bean
    public NewTopic orderStatusTopic() {
        return TopicBuilder.name(ORDER_STATUS_TOPIC)
                           .partitions(3)
                           .replicas(1)
                           .build();
    }
}