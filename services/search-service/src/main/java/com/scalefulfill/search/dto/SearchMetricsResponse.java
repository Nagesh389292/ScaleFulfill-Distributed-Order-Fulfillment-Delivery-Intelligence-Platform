package com.scalefulfill.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchMetricsResponse {
    private long totalOrdersIndexed;
    private long totalProductsIndexed;
    private double avgIndexingLagMs;
    private long lastMeasuredLagMs;
    private String clusterStatus;
    private Instant lastIndexedAt;
    private String ordersIndexName;
    private String productsIndexName;
}
