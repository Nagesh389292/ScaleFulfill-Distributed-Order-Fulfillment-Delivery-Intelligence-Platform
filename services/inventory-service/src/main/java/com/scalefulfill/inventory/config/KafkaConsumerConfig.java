package com.scalefulfill.inventory.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    public static final String DLQ_SUFFIX = ".DLQ";

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            ObjectProvider<KafkaTemplate<String, String>> kafkaTemplateProvider) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        KafkaTemplate<String, String> kafkaTemplate = kafkaTemplateProvider.getIfAvailable();
        if (kafkaTemplate != null) {
            DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                    (KafkaOperations<String, String>) kafkaTemplate,
                    (record, ex) -> {
                        log.warn("[inventory-dlq] Routing poison message from topic [{}] partition [{}] to DLQ [{}] due to: {}",
                                record.topic(), record.partition(), record.topic() + DLQ_SUFFIX, ex.getMessage());
                        return new TopicPartition(record.topic() + DLQ_SUFFIX, record.partition());
                    }
            );
            DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(500L, 3L));
            factory.setCommonErrorHandler(errorHandler);
        } else {
            factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(0L, 0L)));
        }

        return factory;
    }
}
