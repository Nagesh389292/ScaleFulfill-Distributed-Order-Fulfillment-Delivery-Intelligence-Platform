package com.scalefulfill.prediction.optimization.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public class AllocationModels {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllocationOrder {
        private String orderId;
        private String customerId;
        private double latitude;
        private double longitude;
        private Map<String, Integer> itemDemands; // productId -> quantity
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllocationFc {
        private String fcId;
        private String name;
        private double latitude;
        private double longitude;
        private int maxCapacity;
        private double baseCost;
        private double costPerKm;
        private Map<String, Integer> availableInventory; // productId -> quantity
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllocationRequest {
        private String batchId;
        private List<AllocationOrder> orders;
        private List<AllocationFc> fulfillmentCenters;
        @Builder.Default
        private long solverTimeoutMs = 3000;
        @Builder.Default
        private double imbalancePenaltyWeight = 0.50;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderAssignment {
        private String orderId;
        private String assignedFcId;
        private double distanceKm;
        private double shippingCost;
        private double fulfillmentCost;
        private double totalCost;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AllocationResult {
        private String batchId;
        private String algorithm; // "GREEDY_NEAREST_FEASIBLE" vs "OR_TOOLS_MILP"
        private String status;    // "OPTIMAL", "FEASIBLE", "INFEASIBLE", "TIMEOUT"
        private int batchSize;
        private int allocatedCount;
        private int unallocatedCount;
        private double totalCost;
        private double solveTimeMs;
        private double optimalityGap;
        private Double bestBound;
        private Map<String, Integer> fcUtilization; // fcId -> assigned count
        private double capacityUtilizationStdDev;
        private List<OrderAssignment> assignments;
    }
}
