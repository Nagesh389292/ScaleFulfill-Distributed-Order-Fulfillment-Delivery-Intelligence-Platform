package com.scalefulfill.order.service;

import com.scalefulfill.common.OrderStatus;
import com.scalefulfill.common.OrderStateMachine;
import com.scalefulfill.common.exception.ResourceNotFoundException;
import com.scalefulfill.inventory.dto.ReservationRequest;
import com.scalefulfill.inventory.dto.ReservationResponse;
import com.scalefulfill.inventory.entity.Product;
import com.scalefulfill.inventory.repository.ProductRepository;
import com.scalefulfill.inventory.service.InventoryService;
import com.scalefulfill.order.dto.CreateOrderItemDto;
import com.scalefulfill.order.dto.CreateOrderRequest;
import com.scalefulfill.order.dto.OrderItemResponse;
import com.scalefulfill.order.dto.OrderResponse;
import com.scalefulfill.order.entity.Order;
import com.scalefulfill.order.entity.OrderItem;
import com.scalefulfill.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;
    private final OrderStateMachine orderStateMachine;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Initiating order creation [{}] for customer [{}]", orderId, request.getCustomerId());

        // 1. Initial State: PENDING
        Order order = Order.builder()
                .id(orderId)
                .customerId(request.getCustomerId())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .items(new ArrayList<>())
                .build();

        // 2. Transition PENDING -> INVENTORY_CHECK
        orderStateMachine.validateTransition(orderId, order.getStatus(), OrderStatus.INVENTORY_CHECK);
        order.setStatus(OrderStatus.INVENTORY_CHECK);

        BigDecimal total = BigDecimal.ZERO;
        String chosenFcId = null;

        // 3. Process items & reserve inventory
        for (CreateOrderItemDto itemDto : request.getItems()) {
            Product product = productRepository.findById(itemDto.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemDto.getProductId()));

            // Reserve inventory (throws InsufficientInventoryException if unavailable)
            ReservationRequest reservationRequest = ReservationRequest.builder()
                    .productId(itemDto.getProductId())
                    .fulfillmentCenterId(chosenFcId) // keep same FC if already selected
                    .quantity(itemDto.getQuantity())
                    .build();

            ReservationResponse reservation = inventoryService.reserveInventory(reservationRequest);
            if (chosenFcId == null) {
                chosenFcId = reservation.getFulfillmentCenterId();
            }

            BigDecimal unitPrice = product.getPrice();
            BigDecimal itemSubtotal = unitPrice.multiply(BigDecimal.valueOf(itemDto.getQuantity()));
            total = total.add(itemSubtotal);

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productId(product.getId())
                    .quantity(itemDto.getQuantity())
                    .unitPrice(unitPrice)
                    .build();

            order.addItem(orderItem);
        }

        // 4. Transition INVENTORY_CHECK -> RESERVED
        orderStateMachine.validateTransition(orderId, order.getStatus(), OrderStatus.RESERVED);
        order.setStatus(OrderStatus.RESERVED);
        order.setTotalAmount(total);
        order.setAssignedFcId(chosenFcId);
        order.setUpdatedAt(Instant.now());

        Order savedOrder = orderRepository.save(order);
        log.info("Order [{}] successfully created and stock reserved at FC [{}] with total [{}]",
                orderId, chosenFcId, total);

        return mapToOrderResponse(savedOrder);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return mapToOrderResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(String orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        log.info("Attempting cancellation for order [{}] in status [{}]", orderId, order.getStatus());

        // Validate state transition (e.g. SHIPPED or DELIVERED cannot be cancelled)
        orderStateMachine.validateTransition(orderId, order.getStatus(), OrderStatus.CANCELLED);

        // Release reserved inventory if stock was held
        if (order.getStatus() == OrderStatus.RESERVED ||
            order.getStatus() == OrderStatus.FULFILLMENT_ASSIGNED ||
            order.getStatus() == OrderStatus.PROCESSING) {

            if (order.getAssignedFcId() != null) {
                for (OrderItem item : order.getItems()) {
                    inventoryService.releaseInventory(item.getProductId(), order.getAssignedFcId(), item.getQuantity());
                }
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());

        Order updatedOrder = orderRepository.save(order);
        log.info("Order [{}] cancelled and inventory released successfully", orderId);
        return mapToOrderResponse(updatedOrder);
    }

    private OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream().map(item -> {
            BigDecimal subtotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            return OrderItemResponse.builder()
                    .itemId(item.getId())
                    .productId(item.getProductId())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .subtotal(subtotal)
                    .build();
        }).collect(Collectors.toList());

        return OrderResponse.builder()
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .assignedFcId(order.getAssignedFcId())
                .version(order.getVersion())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .items(itemResponses)
                .build();
    }
}
