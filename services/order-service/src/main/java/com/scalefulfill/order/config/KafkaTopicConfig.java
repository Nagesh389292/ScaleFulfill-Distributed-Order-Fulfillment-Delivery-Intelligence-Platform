package com.scalefulfill.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "spring.kafka.admin.auto-create", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    public static final String TOPIC_ORDER_CREATED = "order.events.created";
    public static final String TOPIC_INVENTORY_RESERVED = "inventory.events.reserved";
    public static final String TOPIC_INVENTORY_FAILED = "inventory.events.failed";
    public static final String TOPIC_ORDER_DLQ = "order.events.dlq";

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name(TOPIC_ORDER_CREATED)
                .partitions(4)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic inventoryReservedTopic() {
        return TopicBuilder.name(TOPIC_INVENTORY_RESERVED)
                .partitions(4)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic inventoryFailedTopic() {
        return TopicBuilder.name(TOPIC_INVENTORY_FAILED)
                .partitions(4)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderDlqTopic() {
        return TopicBuilder.name(TOPIC_ORDER_DLQ)
                .partitions(4)
                .replicas(1)
                .build();
    }
}
