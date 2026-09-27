package com.scalefulfill.search.consumer;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalefulfill.search.event.EventEnvelope;
import com.scalefulfill.search.event.OrderCreatedPayload;
import com.scalefulfill.search.metrics.SearchMetrics;
import com.scalefulfill.search.model.OrderDocument;
import com.scalefulfill.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventSearchConsumer {

    public static final String CONSUMER_GROUP = "search-service-group";
    public static final String TOPIC_ORDER_CREATED = "order.events.created";

    private final SearchService searchService;
    private final ObjectMapper objectMapper;
    private final SearchMetrics searchMetrics;

    private final AtomicLong consumedCount = new AtomicLong(0);

    @KafkaListener(
            topics = "${scalefulfill.kafka.topics.order-created:order.events.created}",
            groupId = "${spring.kafka.consumer.group-id:search-service-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(String message, Acknowledgment ack) {
        log.info("[search-consumer] Received event payload from Kafka: {}", message);
        try {
            EventEnvelope<OrderCreatedPayload> envelope;
            try {
                JavaType type = objectMapper.getTypeFactory().constructParametricType(EventEnvelope.class, OrderCreatedPayload.class);
                envelope = objectMapper.readValue(message, type);
            } catch (Exception e) {
                // Fallback direct payload parsing
                OrderCreatedPayload directPayload = objectMapper.readValue(message, OrderCreatedPayload.class);
                envelope = EventEnvelope.<OrderCreatedPayload>builder()
                        .eventId("fallback-" + System.currentTimeMillis())
                        .eventType("OrderCreatedEvent")
                        .aggregateId(directPayload.getOrderId())
                        .occurredAt(Instant.now())
                        .payload(directPayload)
                        .build();
            }

            OrderCreatedPayload payload = envelope.getPayload();
            if (payload == null || payload.getOrderId() == null) {
                log.warn("[search-consumer] Received empty or invalid order payload, skipping.");
                if (ack != null) ack.acknowledge();
                return;
            }

            Instant occurredAt = envelope.getOccurredAt() != null ? envelope.getOccurredAt() : Instant.now();
            long lagMs = Math.max(0, Duration.between(occurredAt, Instant.now()).toMillis());
            searchService.recordIndexingLag(lagMs);

            List<OrderDocument.OrderItemDoc> itemDocs = new ArrayList<>();
            StringBuilder searchTextBuilder = new StringBuilder();
            searchTextBuilder.append(payload.getOrderId()).append(" ").append(payload.getCustomerId());

            if (payload.getItems() != null) {
                for (OrderCreatedPayload.OrderItemPayload item : payload.getItems()) {
                    itemDocs.add(OrderDocument.OrderItemDoc.builder()
                            .productId(item.getProductId())
                            .productName("Product " + item.getProductId())
                            .quantity(item.getQuantity())
                            .unitPrice(item.getUnitPrice())
                            .build());
                    searchTextBuilder.append(" ").append(item.getProductId());
                }
            }

            OrderDocument orderDoc = OrderDocument.builder()
                    .orderId(payload.getOrderId())
                    .customerId(payload.getCustomerId())
                    .status("CREATED")
                    .totalAmount(payload.getTotalAmount())
                    .createdAt(occurredAt)
                    .updatedAt(Instant.now())
                    .searchText(searchTextBuilder.toString())
                    .items(itemDocs)
                    .build();

            // Index into OpenSearch
            try {
                searchService.indexOrder(orderDoc);
                searchMetrics.getIndexingSuccessCounter().increment();
                consumedCount.incrementAndGet();
                log.info("[search-consumer] Order [{}] successfully projected into OpenSearch. Measured lag: {} ms",
                        payload.getOrderId(), lagMs);
            } catch (Exception indexEx) {
                searchMetrics.getIndexingErrorCounter().increment();
                log.error("[search-consumer] OpenSearch indexing failed for order [{}]: {}",
                        payload.getOrderId(), indexEx.getMessage());
                throw new RuntimeException("Search indexing failed, message will be retried: " + indexEx.getMessage(), indexEx);
            }

            if (ack != null) {
                ack.acknowledge();
            }
        } catch (RuntimeException re) {
            // Already logged above — re-throw to prevent offset commit
            throw re;
        } catch (Exception e) {
            log.error("[search-consumer] Failed to process search projection event: {}", message, e);
            throw new RuntimeException("Search indexing failed, message will be retried: " + e.getMessage(), e);
        }
    }

    public long getConsumedCount() {
        return consumedCount.get();
    }

    public void resetConsumedCount() {
        consumedCount.set(0);
    }
}
