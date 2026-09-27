package com.scalefulfill.prediction.model;

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
public class OrderPredictionEvent {
    private String eventId;
    private String orderId;
    private String customerId;
    private Instant occurredAt;
    private BigDecimal totalAmount;
    private List<PredictionItem> items;
    private List<String> candidateFulfillmentCenters;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionItem {
        private String productId;
        private Integer quantity;
        private BigDecimal unitPrice;
        private Double weightKg;
    }
}
