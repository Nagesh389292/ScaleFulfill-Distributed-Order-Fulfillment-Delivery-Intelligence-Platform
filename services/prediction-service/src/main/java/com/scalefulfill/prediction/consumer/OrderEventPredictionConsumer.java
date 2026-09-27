package com.scalefulfill.prediction.consumer;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalefulfill.prediction.event.EventEnvelope;
import com.scalefulfill.prediction.event.OrderCreatedPayload;
import com.scalefulfill.prediction.metrics.PredictionMetrics;
import com.scalefulfill.prediction.model.OrderPredictionEvent;
import com.scalefulfill.prediction.pool.PredictionWorkerPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventPredictionConsumer {

    public static final String CONSUMER_GROUP = "prediction-service-group";
    public static final String TOPIC_ORDER_CREATED = "order.events.created";

    private final PredictionWorkerPool workerPool;
    private final ObjectMapper objectMapper;
    private final PredictionMetrics metrics;

    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();
    private final AtomicLong consumedCount = new AtomicLong(0);

    @KafkaListener(
            topics = "${scalefulfill.kafka.topics.order-created:order.events.created}",
            groupId = "${spring.kafka.consumer.group-id:prediction-service-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(String message, Acknowledgment ack) {
        consumedCount.incrementAndGet();
        log.info("[prediction-consumer] Consumed order event from Kafka: {}", message);

        try {
            EventEnvelope<OrderCreatedPayload> envelope;
            try {
                JavaType type = objectMapper.getTypeFactory().constructParametricType(EventEnvelope.class, OrderCreatedPayload.class);
                envelope = objectMapper.readValue(message, type);
            } catch (Exception e) {
                com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(message);
                if (root.has("payload")) {
                    OrderCreatedPayload p = objectMapper.treeToValue(root.get("payload"), OrderCreatedPayload.class);
                    envelope = EventEnvelope.<OrderCreatedPayload>builder()
                            .eventId(root.has("eventId") ? root.get("eventId").asText() : "evt-" + System.currentTimeMillis())
                            .eventType(root.has("eventType") ? root.get("eventType").asText() : "OrderCreatedEvent")
                            .aggregateId(root.has("aggregateId") ? root.get("aggregateId").asText() : (p != null ? p.getOrderId() : null))
                            .occurredAt(Instant.now())
                            .payload(p)
                            .build();
                } else {
                    OrderCreatedPayload directPayload = objectMapper.readValue(message, OrderCreatedPayload.class);
                    envelope = EventEnvelope.<OrderCreatedPayload>builder()
                            .eventId("fallback-" + System.currentTimeMillis())
                            .eventType("OrderCreatedEvent")
                            .aggregateId(directPayload.getOrderId())
                            .occurredAt(Instant.now())
                            .payload(directPayload)
                            .build();
                }
            }

            String eventId = envelope.getEventId();
            if (eventId != null && !processedEventIds.add(eventId)) {
                log.info("[prediction-consumer] Deduplicated event {} for order {} (Inbox pattern)",
                        eventId, envelope.getAggregateId());
                if (ack != null) {
                    ack.acknowledge();
                }
                return;
            }

            OrderCreatedPayload payload = envelope.getPayload();
            if (payload == null || payload.getOrderId() == null) {
                log.warn("[prediction-consumer] Payload missing orderId, skipping");
                if (ack != null) {
                    ack.acknowledge();
                }
                return;
            }

            List<OrderPredictionEvent.PredictionItem> items = new ArrayList<>();
            if (payload.getItems() != null) {
                for (OrderCreatedPayload.OrderItemPayload item : payload.getItems()) {
                    items.add(OrderPredictionEvent.PredictionItem.builder()
                            .productId(item.getProductId())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .weightKg(0.5)
                            .build());
                }
            }

            OrderPredictionEvent predictionEvent = OrderPredictionEvent.builder()
                    .eventId(eventId)
                    .orderId(payload.getOrderId())
                    .customerId(payload.getCustomerId() != null ? payload.getCustomerId() : "CUST-1001")
                    .occurredAt(envelope.getOccurredAt() != null ? envelope.getOccurredAt() : Instant.now())
                    .totalAmount(payload.getTotalAmount())
                    .items(items)
                    .build();

            // Submit to bounded prediction worker pool asynchronously
            workerPool.submitPredictionTask(predictionEvent);

            if (ack != null) {
                ack.acknowledge();
            }
        } catch (Exception e) {
            metrics.recordFailure();
            log.error("[prediction-consumer] Error processing prediction event: {}", e.getMessage(), e);
            throw new RuntimeException("Prediction event processing failed", e);
        }
    }

    public long getConsumedCount() {
        return consumedCount.get();
    }

    public void clearInbox() {
        processedEventIds.clear();
        consumedCount.set(0);
    }
}
