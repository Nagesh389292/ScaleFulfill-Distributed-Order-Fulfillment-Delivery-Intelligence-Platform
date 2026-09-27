package com.scalefulfill.search.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scalefulfill.search.event.EventEnvelope;
import com.scalefulfill.search.event.OrderCreatedPayload;
import com.scalefulfill.search.model.OrderDocument;
import com.scalefulfill.search.service.SearchService;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventSearchConsumerTest {

    @Mock
    private SearchService searchService;

    @Mock
    private Acknowledgment acknowledgment;

    private ObjectMapper objectMapper;
    private OrderEventSearchConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        consumer = new OrderEventSearchConsumer(searchService, objectMapper);
    }

    @Test
    @DisplayName("Should consume OrderCreated event and project into OpenSearch")
    void shouldConsumeOrderCreatedEvent() throws Exception {
        OrderCreatedPayload payload = OrderCreatedPayload.builder()
                .orderId("ord-kafka-001")
                .customerId("cust-999")
                .totalAmount(new BigDecimal("199.99"))
                .items(List.of(
                        OrderCreatedPayload.OrderItemPayload.builder()
                                .productId("prod-mouse-01")
                                .quantity(2)
                                .unitPrice(new BigDecimal("99.99"))
                                .build()
                ))
                .build();

        EventEnvelope<OrderCreatedPayload> envelope = EventEnvelope.<OrderCreatedPayload>builder()
                .eventId("evt-001")
                .eventType("OrderCreatedEvent")
                .aggregateType("Order")
                .aggregateId("ord-kafka-001")
                .occurredAt(Instant.now().minusMillis(25))
                .correlationId("corr-123")
                .payload(payload)
                .build();

        String json = objectMapper.writeValueAsString(envelope);

        consumer.onMessage(json, acknowledgment);

        ArgumentCaptor<OrderDocument> captor = ArgumentCaptor.forClass(OrderDocument.class);
        verify(searchService).indexOrder(captor.capture());
        verify(searchService).recordIndexingLag(anyLong());
        verify(acknowledgment).acknowledge();

        OrderDocument doc = captor.getValue();
        assertEquals("ord-kafka-001", doc.getOrderId());
        assertEquals("cust-999", doc.getCustomerId());
        assertEquals("CREATED", doc.getStatus());
        assertEquals(new BigDecimal("199.99"), doc.getTotalAmount());
        assertEquals(1, doc.getItems().size());
        assertEquals(1, consumer.getConsumedCount());
    }
}
