package com.scalefulfill.order.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scalefulfill.order.dto.CreateOrderItemDto;
import com.scalefulfill.order.dto.CreateOrderRequest;
import com.scalefulfill.order.dto.OrderResponse;
import com.scalefulfill.order.entity.Order;
import com.scalefulfill.order.repository.OrderRepository;
import com.scalefulfill.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Transactional Outbox Pattern - Atomic Persistence Tests")
class TransactionalOutboxTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Persists Order and OutboxEvent atomically in single local DB transaction")
    void testAtomicOrderAndOutboxPersistence() {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId("CUST-1001")
                .items(List.of(
                        CreateOrderItemDto.builder()
                                .productId("PROD-100")
                                .quantity(2)
                                .unitPrice(BigDecimal.valueOf(1499.00))
                                .build()
                ))
                .build();

        String correlationId = "CORR-TEST-OUTBOX-001";
        OrderResponse response = orderService.createOrderWithOutbox(request, correlationId);

        assertNotNull(response);
        assertNotNull(response.getOrderId());

        // 1. Verify Order persisted in database
        Optional<Order> orderOpt = orderRepository.findById(response.getOrderId());
        assertTrue(orderOpt.isPresent(), "Order must be committed to order_db");
        Order order = orderOpt.get();
        assertEquals("CUST-1001", order.getCustomerId());
        assertEquals(BigDecimal.valueOf(2998.00), order.getTotalAmount());

        // 2. Verify Outbox Event persisted atomically in outbox_events table
        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();
        Optional<OutboxEvent> outboxOpt = outboxEvents.stream()
                .filter(e -> response.getOrderId().equals(e.getAggregateId()))
                .findFirst();

        assertTrue(outboxOpt.isPresent(), "OutboxEvent must exist in outbox_events table");
        OutboxEvent event = outboxOpt.get();
        assertEquals("ORDER", event.getAggregateType());
        assertEquals(response.getOrderId(), event.getAggregateId());
        assertEquals("OrderCreatedEvent", event.getEventType());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(correlationId, event.getCorrelationId());
        assertEquals(0, event.getRetryCount());
        assertNotNull(event.getPayload());
        assertTrue(event.getPayload().contains("PROD-100"), "Payload must contain product details");
    }
}
