package com.scalefulfill.order.service;

import com.scalefulfill.common.OrderStatus;
import com.scalefulfill.common.OrderStateMachine;
import com.scalefulfill.common.exception.InsufficientInventoryException;
import com.scalefulfill.common.exception.InvalidOrderStateException;
import com.scalefulfill.common.exception.ResourceNotFoundException;
import com.scalefulfill.inventory.dto.ReservationRequest;
import com.scalefulfill.inventory.dto.ReservationResponse;
import com.scalefulfill.inventory.entity.Product;
import com.scalefulfill.inventory.repository.ProductRepository;
import com.scalefulfill.inventory.service.InventoryService;
import com.scalefulfill.order.dto.CreateOrderItemDto;
import com.scalefulfill.order.dto.CreateOrderRequest;
import com.scalefulfill.order.dto.OrderResponse;
import com.scalefulfill.order.entity.Order;
import com.scalefulfill.order.entity.OrderItem;
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
@DisplayName("Order Service Unit Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private InventoryService inventoryService;

    @Spy
    private OrderStateMachine orderStateMachine = new OrderStateMachine();

    @InjectMocks
    private OrderServiceImpl orderService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id("PROD-100")
                .sku("SKU-LAPTOP-X1")
                .name("UltraBook Pro 15 inch")
                .price(BigDecimal.valueOf(64999.00))
                .weightKg(BigDecimal.valueOf(1.85))
                .build();
    }

    @Test
    @DisplayName("Create order successfully when inventory is available")
    void testCreateOrderSuccess() {
        when(productRepository.findById("PROD-100")).thenReturn(Optional.of(product));
        when(inventoryService.reserveInventory(any(ReservationRequest.class))).thenReturn(
                ReservationResponse.builder()
                        .reservationId("RES-123")
                        .productId("PROD-100")
                        .fulfillmentCenterId("FC-BLR-01")
                        .reservedQuantity(2)
                        .remainingAvailableQuantity(48)
                        .reservedAt(Instant.now())
                        .build()
        );
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId("CUST-1001")
                .items(List.of(
                        CreateOrderItemDto.builder()
                                .productId("PROD-100")
                                .quantity(2)
                                .build()
                ))
                .build();

        OrderResponse response = orderService.createOrder(request);

        assertNotNull(response);
        assertEquals(OrderStatus.RESERVED, response.getStatus());
        assertEquals("FC-BLR-01", response.getAssignedFcId());
        assertEquals(BigDecimal.valueOf(129998.00), response.getTotalAmount());
        assertEquals(1, response.getItems().size());
        verify(inventoryService, times(1)).reserveInventory(any());
        verify(orderRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Rollback when inventory reservation fails with InsufficientInventoryException")
    void testCreateOrderInsufficientInventory() {
        when(productRepository.findById("PROD-100")).thenReturn(Optional.of(product));
        when(inventoryService.reserveInventory(any(ReservationRequest.class)))
                .thenThrow(new InsufficientInventoryException("Stock insufficient"));

        CreateOrderRequest request = CreateOrderRequest.builder()
                .customerId("CUST-1001")
                .items(List.of(
                        CreateOrderItemDto.builder()
                                .productId("PROD-100")
                                .quantity(200)
                                .build()
                ))
                .build();

        assertThrows(InsufficientInventoryException.class, () -> orderService.createOrder(request));
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cancel order releases reserved inventory")
    void testCancelOrderReleasesInventory() {
        Order existingOrder = Order.builder()
                .id("ORD-TEST01")
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

        when(orderRepository.findByIdWithItems("ORD-TEST01")).thenReturn(Optional.of(existingOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse cancelledResponse = orderService.cancelOrder("ORD-TEST01");

        assertEquals(OrderStatus.CANCELLED, cancelledResponse.getStatus());
        verify(inventoryService, times(1)).releaseInventory("PROD-100", "FC-BLR-01", 1);
        verify(orderRepository, times(1)).save(existingOrder);
    }

    @Test
    @DisplayName("Cannot cancel order once SHIPPED")
    void testCannotCancelShippedOrder() {
        Order shippedOrder = Order.builder()
                .id("ORD-SHIPPED")
                .customerId("CUST-1001")
                .status(OrderStatus.SHIPPED)
                .totalAmount(BigDecimal.valueOf(64999.00))
                .assignedFcId("FC-BLR-01")
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findByIdWithItems("ORD-SHIPPED")).thenReturn(Optional.of(shippedOrder));

        assertThrows(InvalidOrderStateException.class, () -> orderService.cancelOrder("ORD-SHIPPED"));
        verify(inventoryService, never()).releaseInventory(any(), any(), anyInt());
    }
}
