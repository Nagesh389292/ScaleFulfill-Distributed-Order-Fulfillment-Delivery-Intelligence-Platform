package com.scalefulfill.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservationResponse {
    private String reservationId;
    private String productId;
    private String fulfillmentCenterId;
    private int reservedQuantity;
    private int remainingAvailableQuantity;
    private Instant reservedAt;
}
