package com.scalefulfill.prediction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalefulfill.prediction.consumer.OrderEventPredictionConsumer;
import com.scalefulfill.prediction.event.EventEnvelope;
import com.scalefulfill.prediction.event.OrderCreatedPayload;
import com.scalefulfill.prediction.metrics.PredictionMetrics;
import com.scalefulfill.prediction.pool.PredictionWorkerPool;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderEventPredictionConsumerTest {

    private PredictionWorkerPool workerPool;
    private OrderEventPredictionConsumer consumer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        objectMapper.findAndRegisterModules();
        workerPool = Mockito.mock(PredictionWorkerPool.class);
        PredictionMetrics metrics = new PredictionMetrics(new SimpleMeterRegistry());
        consumer = new OrderEventPredictionConsumer(workerPool, objectMapper, metrics);
    }

    @Test
    @DisplayName("Should consume order event and dispatch to worker pool")
    void testConsumerDispatch() throws Exception {
        OrderCreatedPayload payload = OrderCreatedPayload.builder()
                .orderId("ORD-KAFKA-1")
                .customerId("CUST-1001")
                .totalAmount(new BigDecimal("120.00"))
                .items(List.of(
                        OrderCreatedPayload.OrderItemPayload.builder()
                                .productId("PROD-1001")
                                .quantity(2)
                                .unitPrice(new BigDecimal("60.00"))
                                .build()
                ))
                .build();

        EventEnvelope<OrderCreatedPayload> envelope = EventEnvelope.<OrderCreatedPayload>builder()
                .eventId("EVT-10001")
                .eventType("OrderCreatedEvent")
                .aggregateId("ORD-KAFKA-1")
                .occurredAt(Instant.now())
                .payload(payload)
                .build();

        String messageJson = objectMapper.writeValueAsString(envelope);
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.onMessage(messageJson, ack);

        verify(workerPool, times(1)).submitPredictionTask(any());
        verify(ack, times(1)).acknowledge();
        assertEquals(1, consumer.getConsumedCount());
    }

    @Test
    @DisplayName("Should deduplicate identical eventIds via Inbox pattern")
    void testInboxDeduplication() throws Exception {
        OrderCreatedPayload payload = OrderCreatedPayload.builder()
                .orderId("ORD-KAFKA-DUP")
                .customerId("CUST-1001")
                .totalAmount(new BigDecimal("80.00"))
                .build();

        EventEnvelope<OrderCreatedPayload> envelope = EventEnvelope.<OrderCreatedPayload>builder()
                .eventId("EVT-DUP-999")
                .eventType("OrderCreatedEvent")
                .aggregateId("ORD-KAFKA-DUP")
                .occurredAt(Instant.now())
                .payload(payload)
                .build();

        String messageJson = objectMapper.writeValueAsString(envelope);
        Acknowledgment ack = mock(Acknowledgment.class);

        // First delivery
        consumer.onMessage(messageJson, ack);
        // Duplicate delivery
        consumer.onMessage(messageJson, ack);

        // Worker pool should only be invoked ONCE
        verify(workerPool, times(1)).submitPredictionTask(any());
        // Both messages acknowledged to avoid infinite poison loops
        verify(ack, times(2)).acknowledge();
        assertEquals(2, consumer.getConsumedCount());
    }
}
