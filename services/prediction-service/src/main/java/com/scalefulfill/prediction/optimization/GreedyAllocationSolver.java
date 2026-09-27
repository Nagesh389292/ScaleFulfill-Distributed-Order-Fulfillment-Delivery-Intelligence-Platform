package com.scalefulfill.prediction.optimization;

import com.scalefulfill.prediction.optimization.model.AllocationModels.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Greedy heuristic baseline: Iterates orders sequentially, assigning each to the nearest feasible FC
 * possessing sufficient capacity and inventory.
 */
@Slf4j
@Component
public class GreedyAllocationSolver {

    public AllocationResult solve(AllocationRequest request) {
        long startTime = System.nanoTime();
        List<AllocationOrder> orders = request.getOrders();
        List<AllocationFc> fcs = request.getFulfillmentCenters();

        // Track remaining capacity & inventory per FC
        Map<String, Integer> remainingCapacity = new HashMap<>();
        Map<String, Map<String, Integer>> remainingInventory = new HashMap<>();
        Map<String, Integer> fcUtilization = new HashMap<>();

        for (AllocationFc fc : fcs) {
            remainingCapacity.put(fc.getFcId(), fc.getMaxCapacity());
            fcUtilization.put(fc.getFcId(), 0);
            Map<String, Integer> invCopy = new HashMap<>();
            if (fc.getAvailableInventory() != null) {
                invCopy.putAll(fc.getAvailableInventory());
            }
            remainingInventory.put(fc.getFcId(), invCopy);
        }

        List<OrderAssignment> assignments = new ArrayList<>();
        double totalCost = 0.0;
        int unallocated = 0;

        for (AllocationOrder order : orders) {
            AllocationFc bestFc = null;
            double lowestCost = Double.POSITIVE_INFINITY;
            double bestDistanceKm = 0.0;

            for (AllocationFc fc : fcs) {
                String fcId = fc.getFcId();
                if (remainingCapacity.get(fcId) <= 0) {
                    continue;
                }

                // Check inventory feasibility
                boolean hasStock = true;
                if (order.getItemDemands() != null) {
                    Map<String, Integer> fcStock = remainingInventory.get(fcId);
                    for (Map.Entry<String, Integer> entry : order.getItemDemands().entrySet()) {
                        int available = fcStock.getOrDefault(entry.getKey(), 0);
                        if (available < entry.getValue()) {
                            hasStock = false;
                            break;
                        }
                    }
                }

                if (!hasStock) {
                    continue;
                }

                double distanceKm = OptimizationCostUtil.haversineDistanceKm(
                        order.getLatitude(), order.getLongitude(),
                        fc.getLatitude(), fc.getLongitude());
                double shippingCost = fc.getBaseCost() + (distanceKm * fc.getCostPerKm());

                if (shippingCost < lowestCost) {
                    lowestCost = shippingCost;
                    bestFc = fc;
                    bestDistanceKm = distanceKm;
                }
            }

            if (bestFc != null) {
                String fcId = bestFc.getFcId();
                // Deduct capacity & inventory
                remainingCapacity.put(fcId, remainingCapacity.get(fcId) - 1);
                fcUtilization.put(fcId, fcUtilization.get(fcId) + 1);

                if (order.getItemDemands() != null) {
                    Map<String, Integer> fcStock = remainingInventory.get(fcId);
                    for (Map.Entry<String, Integer> entry : order.getItemDemands().entrySet()) {
                        fcStock.put(entry.getKey(), fcStock.get(entry.getKey()) - entry.getValue());
                    }
                }

                totalCost += lowestCost;
                assignments.add(OrderAssignment.builder()
                        .orderId(order.getOrderId())
                        .assignedFcId(fcId)
                        .distanceKm(Math.round(bestDistanceKm * 10.0) / 10.0)
                        .shippingCost(Math.round(lowestCost * 100.0) / 100.0)
                        .fulfillmentCost(bestFc.getBaseCost())
                        .totalCost(Math.round(lowestCost * 100.0) / 100.0)
                        .build());
            } else {
                unallocated++;
            }
        }

        long elapsedNs = System.nanoTime() - startTime;
        double solveTimeMs = elapsedNs / 1_000_000.0;
        double stdDev = OptimizationCostUtil.calculateStdDev(fcUtilization.values());

        String status = unallocated == 0 ? "FEASIBLE" : (unallocated < orders.size() ? "PARTIALLY_FEASIBLE" : "INFEASIBLE");

        return AllocationResult.builder()
                .batchId(request.getBatchId())
                .algorithm("GREEDY_NEAREST_FEASIBLE")
                .status(status)
                .batchSize(orders.size())
                .allocatedCount(assignments.size())
                .unallocatedCount(unallocated)
                .totalCost(Math.round(totalCost * 100.0) / 100.0)
                .solveTimeMs(Math.round(solveTimeMs * 100.0) / 100.0)
                .optimalityGap(0.0) // Heuristic has no mathematical lower bound
                .bestBound(null)
                .fcUtilization(fcUtilization)
                .capacityUtilizationStdDev(Math.round(stdDev * 100.0) / 100.0)
                .assignments(assignments)
                .build();
    }
}
