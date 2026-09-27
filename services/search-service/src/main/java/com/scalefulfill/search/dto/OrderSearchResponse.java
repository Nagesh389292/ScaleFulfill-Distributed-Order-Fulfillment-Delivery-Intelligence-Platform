package com.scalefulfill.search.dto;

import com.scalefulfill.search.model.OrderDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderSearchResponse {
    private List<OrderDocument> results;
    private long totalHits;
    private int page;
    private int size;
    private long executionTimeMs;
    private Map<String, Long> statusAggregation;
    private Double totalAmountSum;
}
