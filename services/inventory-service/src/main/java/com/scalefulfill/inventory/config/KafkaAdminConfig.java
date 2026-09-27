package com.scalefulfill.inventory.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;

/**
 * Provides a Kafka AdminClient bean used by InventoryMetrics to poll consumer
 * lag. AdminClient is independent of the listener container and does not affect
 * message processing.
 *
 * <p>{@code @EnableScheduling} is placed here rather than on the application
 * class to keep scheduling opt-in and scoped to the observability layer.</p>
 */
@Configuration
@EnableScheduling
public class KafkaAdminConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public AdminClient kafkaAdminClient() {
        return AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000",
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "8000"
        ));
    }
}
