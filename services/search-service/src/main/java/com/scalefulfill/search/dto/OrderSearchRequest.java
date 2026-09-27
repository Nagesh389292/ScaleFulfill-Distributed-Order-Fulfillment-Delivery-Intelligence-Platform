package com.scalefulfill.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSearchRequest {
    private String query;
    private String customerId;
    private String status;
    private String fcId;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Instant fromDate;
    private Instant toDate;
    @Builder.Default
    private int page = 0;
    @Builder.Default
    private int size = 20;
    @Builder.Default
    private String sortBy = "created_at";
    @Builder.Default
    private String sortOrder = "desc";
}
