package com.scalefulfill.order.outbox;

import com.scalefulfill.order.config.KafkaTopicConfig;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

@DisplayName("Outbox Publisher - Polling & Kafka Streaming Tests")
class OutboxPublisherTest {

    @Test
    @DisplayName("Publishes pending outbox events to Kafka and marks them PUBLISHED")
    void testPublishPendingEvents() {
        OutboxEventRepository repo = Mockito.mock(OutboxEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = Mockito.mock(KafkaTemplate.class);

        OutboxEvent event = OutboxEvent.builder()
                .id("EVT-1001")
                .aggregateType("ORDER")
                .aggregateId("ORD-999")
                .eventType("OrderCreatedEvent")
                .payload("{\"orderId\":\"ORD-999\"}")
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        Mockito.when(repo.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any()))
                .thenReturn(List.of(event));

        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(KafkaTopicConfig.TOPIC_ORDER_CREATED, 2), 0L, 0, 0L, 0, 0);
        SendResult<String, String> sendResult = new SendResult<>(null, metadata);

        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(sendResult);
        Mockito.when(kafkaTemplate.send(eq(KafkaTopicConfig.TOPIC_ORDER_CREATED), eq("ORD-999"), anyString()))
                .thenReturn(future);

        OutboxPublisher publisher = new OutboxPublisher(repo, kafkaTemplate);
        publisher.publishPendingEvents();

        Mockito.verify(kafkaTemplate).send(eq(KafkaTopicConfig.TOPIC_ORDER_CREATED), eq("ORD-999"), eq(event.getPayload()));
        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getProcessedAt());
        Mockito.verify(repo).save(event);
    }

    @Test
    @DisplayName("Increments retry count on Kafka publish failure")
    void testHandleKafkaPublishFailure() {
        OutboxEventRepository repo = Mockito.mock(OutboxEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = Mockito.mock(KafkaTemplate.class);

        OutboxEvent event = OutboxEvent.builder()
                .id("EVT-1002")
                .aggregateType("ORDER")
                .aggregateId("ORD-888")
                .eventType("OrderCreatedEvent")
                .payload("{\"orderId\":\"ORD-888\"}")
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        Mockito.when(repo.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any()))
                .thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka broker unreachable"));

        Mockito.when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failedFuture);

        OutboxPublisher publisher = new OutboxPublisher(repo, kafkaTemplate);
        publisher.publishPendingEvents();

        assertEquals(1, event.getRetryCount());
        Mockito.verify(repo).save(event);
    }
}
