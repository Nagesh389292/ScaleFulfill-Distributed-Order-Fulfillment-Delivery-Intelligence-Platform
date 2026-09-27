package com.scalefulfill.inventory.inbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalefulfill.inventory.dto.ReservationRequest;
import com.scalefulfill.inventory.event.EventEnvelope;
import com.scalefulfill.inventory.event.OrderCreatedPayload;
import com.scalefulfill.inventory.exception.InsufficientInventoryException;
import com.scalefulfill.inventory.exception.ResourceNotFoundException;
import com.scalefulfill.inventory.metrics.InventoryMetrics;
import com.scalefulfill.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCreatedEventConsumer {

    public static final String CONSUMER_GROUP = "inventory-service-group";
    public static final String TOPIC_ORDER_CREATED = "order.events.created";

    private final ProcessedEventRepository processedEventRepository;
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;
    private final InventoryMetrics inventoryMetrics;

    // AtomicLong counters retained for backward-compatible test assertions
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong duplicateCount = new AtomicLong(0);
    private final AtomicLong failedReservationCount = new AtomicLong(0);

    @KafkaListener(
            topics = "${scalefulfill.kafka.topics.order-created:order.events.created}",
            groupId = "${spring.kafka.consumer.group-id:inventory-service-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(String payload, Acknowledgment ack) {
        log.info("[inventory-consumer] Received raw Kafka message: {}", payload);
        try {
            JavaType type = objectMapper.getTypeFactory().constructParametricType(EventEnvelope.class, OrderCreatedPayload.class);
            EventEnvelope<OrderCreatedPayload> envelope = objectMapper.readValue(payload, type);

            consumeEvent(envelope);

            if (ack != null) {
                ack.acknowledge();
            }
        } catch (JsonProcessingException e) {
            log.error("[inventory-consumer] Malformed event payload, poison pill detected: {}", payload, e);
            // Re-throw to trigger DefaultErrorHandler -> Retry -> DLQ routing
            throw new RuntimeException("Malformed Kafka event payload", e);
        } catch (Exception e) {
            log.error("[inventory-consumer] Error processing Kafka event: {}", payload, e);
            throw e;
        }
    }

    /**
     * Idempotent business execution.
     * Guarantees at-most-once business side effects even under at-least-once event delivery.
     *
     * @param envelope EventEnvelope containing OrderCreatedPayload
     * @return true if newly processed, false if duplicate ignored
     */
    @Transactional
    public boolean consumeEvent(EventEnvelope<OrderCreatedPayload> envelope) {
        String eventId = envelope.getEventId();
        String eventType = envelope.getEventType();
        OrderCreatedPayload orderData = envelope.getPayload();
        long startNs = System.nanoTime();

        // 1. Idempotency Check: Query processed_events inbox table
        if (processedEventRepository.existsByEventIdAndConsumerGroup(eventId, CONSUMER_GROUP)) {
            duplicateCount.incrementAndGet();
            log.warn("[inventory-consumer] DUPLICATE event detected [{}]. Skipping processing.", eventId);
            long durationNs = System.nanoTime() - startNs;
            inventoryMetrics.getReservationDuplicateSkippedCounter().increment();
            inventoryMetrics.getReservationTimerDuplicateSkipped().record(java.time.Duration.ofNanos(durationNs));
            return false;
        }

        // 2. Perform business operation: Reserve inventory for each order item
        boolean reservationSuccess = true;
        try {
            if (orderData != null && orderData.getItems() != null) {
                for (OrderCreatedPayload.OrderItemPayload item : orderData.getItems()) {
                    ReservationRequest request = ReservationRequest.builder()
                            .productId(item.getProductId())
                            .quantity(item.getQuantity())
                            .fulfillmentCenterId(null) // Best-fit FC allocation
                            .build();

                    inventoryService.reserveInventory(request);
                }
            }
        } catch (InsufficientInventoryException | ResourceNotFoundException e) {
            failedReservationCount.incrementAndGet();
            reservationSuccess = false;
            log.warn("[inventory-consumer] Inventory reservation rejected for order [{}] (event [{}]): {}",
                    orderData != null ? orderData.getOrderId() : "UNKNOWN", eventId, e.getMessage());
        }

        // 3. Persist to inbox table within the same ACID transaction
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(eventId)
                .consumerGroup(CONSUMER_GROUP)
                .eventType(reservationSuccess ? eventType : eventType + "_REJECTED")
                .processedAt(Instant.now())
                .build();

        processedEventRepository.save(processedEvent);
        processedCount.incrementAndGet();

        long durationNs = System.nanoTime() - startNs;
        if (reservationSuccess) {
            inventoryMetrics.getReservationSuccessCounter().increment();
            inventoryMetrics.getReservationTimerReserved().record(java.time.Duration.ofNanos(durationNs));
        } else {
            inventoryMetrics.getReservationInsufficientStockCounter().increment();
            inventoryMetrics.getReservationTimerInsufficient().record(java.time.Duration.ofNanos(durationNs));
        }

        log.info("[inventory-consumer] Successfully recorded event [{}] in inbox. Order [{}], status [{}]",
                eventId, orderData != null ? orderData.getOrderId() : "UNKNOWN",
                reservationSuccess ? "RESERVED" : "INSUFFICIENT_STOCK");

        return true;
    }

    public long getProcessedCount() {
        return processedCount.get();
    }

    public long getDuplicateCount() {
        return duplicateCount.get();
    }

    public long getFailedReservationCount() {
        return failedReservationCount.get();
    }

    public void resetMetrics() {
        processedCount.set(0);
        duplicateCount.set(0);
        failedReservationCount.set(0);
    }
}
