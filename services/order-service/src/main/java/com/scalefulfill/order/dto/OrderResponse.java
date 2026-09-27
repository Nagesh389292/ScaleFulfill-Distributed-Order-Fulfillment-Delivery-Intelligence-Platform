package com.scalefulfill.order.dto;

import com.scalefulfill.order.common.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private String orderId;
    private String customerId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private String assignedFcId;
    private Long version;
    private Instant createdAt;
    private Instant updatedAt;
    private List<OrderItemResponse> items;
}
