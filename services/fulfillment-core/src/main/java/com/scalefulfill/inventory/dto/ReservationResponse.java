package com.scalefulfill.inventory.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationResponse {
    private String reservationId;
    private String productId;
    private String fulfillmentCenterId;
    private int reservedQuantity;
    private int remainingAvailableQuantity;
    private Instant reservedAt;
}
