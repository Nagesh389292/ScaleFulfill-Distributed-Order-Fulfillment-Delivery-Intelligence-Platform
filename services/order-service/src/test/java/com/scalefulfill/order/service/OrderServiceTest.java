package com.scalefulfill.order.service;

import com.scalefulfill.order.client.InventoryClient;
import com.scalefulfill.order.client.dto.InventoryReservationRequest;
import com.scalefulfill.order.client.dto.InventoryReservationResponse;
import com.scalefulfill.order.common.OrderStatus;
import com.scalefulfill.order.common.OrderStateMachine;
import com.scalefulfill.order.dto.CreateOrderItemDto;
import com.scalefulfill.order.dto.CreateOrderRequest;
import com.scalefulfill.order.dto.OrderResponse;
import com.scalefulfill.order.entity.Order;
import com.scalefulfill.order.entity.OrderItem;
import com.scalefulfill.order.exception.DownstreamBusinessException;
import com.scalefulfill.order.exception.InventoryServiceUnavailableException;
import com.scalefulfill.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Order Service Unit & Resilience Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private InventoryClient inventoryClient;

    @Spy
    private OrderStateMachine orderStateMachine = new OrderStateMachine();

    @InjectMocks
    private OrderServiceImpl orderService;

    private CreateOrderRequest createRequest;

    @BeforeEach
    void setUp() {
        createRequest = CreateOrderRequest.builder()
                .customerId("CUST-1001")
                .items(List.of(
                        CreateOrderItemDto.builder()
                                .productId("PROD-100")
                                .quantity(2)
                                .unitPrice(BigDecimal.valueOf(64999.00))
                                .build()
                ))
                .build();
    }

    @Test
    @DisplayName("Create order successfully when inventory service confirms stock reservation")
    void testCreateOrderSuccess() {
        when(inventoryClient.reserveInventory(any(InventoryReservationRequest.class)))
                .thenReturn(InventoryReservationResponse.builder()
                        .reservationId("RES-999")
                        .productId("PROD-100")
                        .fulfillmentCenterId("FC-BLR-01")
                        .reservedQuantity(2)
                        .remainingAvailableQuantity(48)
                        .reservedAt(Instant.now())
                        .build());

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.createOrder(createRequest);

        assertNotNull(response);
        assertEquals(OrderStatus.RESERVED, response.getStatus());
        assertEquals("FC-BLR-01", response.getAssignedFcId());
        assertEquals(BigDecimal.valueOf(129998.00), response.getTotalAmount());
        verify(inventoryClient, times(1)).reserveInventory(any());
        verify(orderRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Downstream 409 Conflict from Inventory marks order REJECTED")
    void testCreateOrderDownstreamInsufficientStock() {
        when(inventoryClient.reserveInventory(any(InventoryReservationRequest.class)))
                .thenThrow(new DownstreamBusinessException(409, "Insufficient stock for SKU"));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(DownstreamBusinessException.class, () -> orderService.createOrder(createRequest));

        verify(orderRepository, times(1)).save(argThat(o -> o.getStatus() == OrderStatus.REJECTED));
    }

    @Test
    @DisplayName("Downstream outage / Circuit Breaker open marks order PENDING_INVENTORY_VERIFICATION (graceful degradation)")
    void testCreateOrderDownstreamOutageFallback() {
        when(inventoryClient.reserveInventory(any(InventoryReservationRequest.class)))
                .thenThrow(new InventoryServiceUnavailableException("Circuit Breaker OPEN"));

        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(InventoryServiceUnavailableException.class, () -> orderService.createOrder(createRequest));

        verify(orderRepository, times(1)).save(argThat(o -> o.getStatus() == OrderStatus.PENDING_INVENTORY_VERIFICATION));
    }

    @Test
    @DisplayName("Cancel order invokes downstream releaseInventory call")
    void testCancelOrderReleasesInventory() {
        Order existingOrder = Order.builder()
                .id("ORD-001")
                .customerId("CUST-1001")
                .status(OrderStatus.RESERVED)
                .totalAmount(BigDecimal.valueOf(64999.00))
                .assignedFcId("FC-BLR-01")
                .version(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .items(new ArrayList<>())
                .build();

        OrderItem item = OrderItem.builder()
                .id(1L)
                .order(existingOrder)
                .productId("PROD-100")
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(64999.00))
                .build();
        existingOrder.addItem(item);

        when(orderRepository.findByIdWithItems("ORD-001")).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse response = orderService.cancelOrder("ORD-001");

        assertEquals(OrderStatus.CANCELLED, response.getStatus());
        verify(inventoryClient, times(1)).releaseInventory(any());
        verify(orderRepository, times(1)).save(existingOrder);
    }
}
