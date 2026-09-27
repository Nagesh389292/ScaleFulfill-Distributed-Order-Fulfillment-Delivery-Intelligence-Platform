package com.scalefulfill.inventory.inbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scalefulfill.inventory.dto.ReservationRequest;
import com.scalefulfill.inventory.dto.ReservationResponse;
import com.scalefulfill.inventory.event.EventEnvelope;
import com.scalefulfill.inventory.event.OrderCreatedPayload;
import com.scalefulfill.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Idempotent Consumer & Inbox Deduplication Tests")
class IdempotentConsumerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private Acknowledgment acknowledgment;

    private ObjectMapper objectMapper;
    private OrderCreatedEventConsumer consumer;

    private EventEnvelope<OrderCreatedPayload> sampleEnvelope;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        consumer = new OrderCreatedEventConsumer(
                processedEventRepository,
                inventoryService,
                objectMapper
        );

        OrderCreatedPayload.OrderItemPayload item = OrderCreatedPayload.OrderItemPayload.builder()
                .productId("PROD-100")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(100.00))
                .build();

        OrderCreatedPayload payload = OrderCreatedPayload.builder()
                .orderId("ORD-999")
                .customerId("CUST-1001")
                .totalAmount(BigDecimal.valueOf(200.00))
                .items(List.of(item))
                .build();

        sampleEnvelope = EventEnvelope.<OrderCreatedPayload>builder()
                .eventId("evt-unique-001")
                .eventType("OrderCreated")
                .aggregateType("Order")
                .aggregateId("ORD-999")
                .occurredAt(Instant.now())
                .correlationId("corr-123")
                .payload(payload)
                .build();
    }

    @Test
    @DisplayName("First event delivery: reserves inventory and records event in inbox")
    void testFirstEventProcessedSuccessfully() {
        when(processedEventRepository.existsByEventIdAndConsumerGroup("evt-unique-001", OrderCreatedEventConsumer.CONSUMER_GROUP))
                .thenReturn(false);

        when(inventoryService.reserveInventory(any(ReservationRequest.class)))
                .thenReturn(ReservationResponse.builder()
                        .reservationId("RES-001")
                        .productId("PROD-100")
                        .fulfillmentCenterId("FC-BLR-01")
                        .reservedQuantity(2)
                        .remainingAvailableQuantity(8)
                        .reservedAt(Instant.now())
                        .build());

        boolean result = consumer.consumeEvent(sampleEnvelope);

        assertTrue(result, "First event should be recognized as new and processed");
        assertEquals(1, consumer.getProcessedCount());
        assertEquals(0, consumer.getDuplicateCount());

        // Verify business side-effect occurred exactly once
        verify(inventoryService, times(1)).reserveInventory(any(ReservationRequest.class));

        // Verify recorded into inbox table
        ArgumentCaptor<ProcessedEvent> eventCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository, times(1)).save(eventCaptor.capture());

        ProcessedEvent savedEvent = eventCaptor.getValue();
        assertEquals("evt-unique-001", savedEvent.getEventId());
        assertEquals(OrderCreatedEventConsumer.CONSUMER_GROUP, savedEvent.getConsumerGroup());
        assertEquals("OrderCreated", savedEvent.getEventType());
    }

    @Test
    @DisplayName("Duplicate event delivery: deduplicated via inbox, business logic skipped")
    void testDuplicateEventDeduplicatedWithoutSideEffects() {
        // Simulate inbox already contains the event ID
        when(processedEventRepository.existsByEventIdAndConsumerGroup("evt-unique-001", OrderCreatedEventConsumer.CONSUMER_GROUP))
                .thenReturn(true);

        boolean result = consumer.consumeEvent(sampleEnvelope);

        assertFalse(result, "Duplicate event should be detected and skipped");
        assertEquals(0, consumer.getProcessedCount());
        assertEquals(1, consumer.getDuplicateCount());

        // CRITICAL: Verify business operation was NEVER executed for duplicate event
        verify(inventoryService, never()).reserveInventory(any());
        // Verify inbox table was NOT re-inserted
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Kafka listener message receipt: processes payload and acknowledges offset")
    void testKafkaMessageReceiptAndAck() throws Exception {
        when(processedEventRepository.existsByEventIdAndConsumerGroup("evt-unique-001", OrderCreatedEventConsumer.CONSUMER_GROUP))
                .thenReturn(false);

        String jsonPayload = objectMapper.writeValueAsString(sampleEnvelope);

        consumer.onMessage(jsonPayload, acknowledgment);

        // Verify offset acknowledged
        verify(acknowledgment, times(1)).acknowledge();
        assertEquals(1, consumer.getProcessedCount());
    }

    @Test
    @DisplayName("Poison pill / malformed message: throws exception triggering retry & DLQ")
    void testPoisonPillTriggersExceptionForDlq() {
        String malformedJson = "{ unparseable_garbage: true,,, }";

        assertThrows(RuntimeException.class, () -> consumer.onMessage(malformedJson, acknowledgment));

        // In case of poison pill, manual offset should NOT be acknowledged before DLQ handling
        verify(acknowledgment, never()).acknowledge();
        assertEquals(0, consumer.getProcessedCount());
    }
}
