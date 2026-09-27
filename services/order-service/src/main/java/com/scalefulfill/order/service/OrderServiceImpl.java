package com.scalefulfill.order.service;

import com.scalefulfill.order.client.InventoryClient;
import com.scalefulfill.order.client.dto.InventoryReleaseRequest;
import com.scalefulfill.order.client.dto.InventoryReservationRequest;
import com.scalefulfill.order.client.dto.InventoryReservationResponse;
import com.scalefulfill.order.common.OrderStatus;
import com.scalefulfill.order.common.OrderStateMachine;
import com.scalefulfill.order.dto.CreateOrderItemDto;
import com.scalefulfill.order.dto.CreateOrderRequest;
import com.scalefulfill.order.dto.OrderItemResponse;
import com.scalefulfill.order.dto.OrderResponse;
import com.scalefulfill.order.entity.Order;
import com.scalefulfill.order.entity.OrderItem;
import com.scalefulfill.order.exception.DownstreamBusinessException;
import com.scalefulfill.order.exception.InventoryServiceUnavailableException;
import com.scalefulfill.order.exception.ResourceNotFoundException;
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
    private final InventoryClient inventoryClient;
    private final OrderStateMachine orderStateMachine;

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[order-service] Initiating distributed order creation [{}] for customer [{}]",
                orderId, request.getCustomerId());

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

        try {
            // 3. Process items and invoke downstream Inventory Service via Resilience4j client
            for (CreateOrderItemDto itemDto : request.getItems()) {
                InventoryReservationRequest resReq = InventoryReservationRequest.builder()
                        .productId(itemDto.getProductId())
                        .fulfillmentCenterId(chosenFcId)
                        .quantity(itemDto.getQuantity())
                        .build();

                // Synchronous HTTP call to inventory-service
                InventoryReservationResponse reservation = inventoryClient.reserveInventory(resReq);

                if (chosenFcId == null) {
                    chosenFcId = reservation.getFulfillmentCenterId();
                }

                BigDecimal unitPrice = itemDto.getUnitPrice() != null ? itemDto.getUnitPrice() : BigDecimal.valueOf(1499.00);
                BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemDto.getQuantity()));
                total = total.add(subtotal);

                OrderItem orderItem = OrderItem.builder()
                        .order(order)
                        .productId(itemDto.getProductId())
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
            log.info("[order-service] Order [{}] successfully created and reserved at FC [{}]",
                    orderId, chosenFcId);
            return mapToOrderResponse(savedOrder);

        } catch (DownstreamBusinessException ex) {
            log.warn("[order-service] Downstream inventory rejection for order [{}]: {}", orderId, ex.getMessage());
            order.setStatus(OrderStatus.REJECTED);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
            throw ex;

        } catch (InventoryServiceUnavailableException ex) {
            log.error("[order-service] Downstream inventory failure for order [{}]. Graceful degradation active.", orderId);
            // In a distributed system, if inventory is down, we park the order as PENDING_INVENTORY_VERIFICATION
            order.setStatus(OrderStatus.PENDING_INVENTORY_VERIFICATION);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);
            throw ex;
        }
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

        log.info("[order-service] Processing cancellation for order [{}] in status [{}]",
                orderId, order.getStatus());

        orderStateMachine.validateTransition(orderId, order.getStatus(), OrderStatus.CANCELLED);

        if (order.getStatus() == OrderStatus.RESERVED ||
            order.getStatus() == OrderStatus.FULFILLMENT_ASSIGNED ||
            order.getStatus() == OrderStatus.PROCESSING) {

            if (order.getAssignedFcId() != null) {
                for (OrderItem item : order.getItems()) {
                    inventoryClient.releaseInventory(InventoryReleaseRequest.builder()
                            .productId(item.getProductId())
                            .fulfillmentCenterId(order.getAssignedFcId())
                            .quantity(item.getQuantity())
                            .build());
                }
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(Instant.now());

        Order updatedOrder = orderRepository.save(order);
        log.info("[order-service] Order [{}] cancelled successfully", orderId);
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
