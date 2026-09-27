package com.scalefulfill.prediction;

import com.scalefulfill.prediction.optimization.GreedyAllocationSolver;
import com.scalefulfill.prediction.optimization.MilpAllocationSolver;
import com.scalefulfill.prediction.optimization.OptimizationService;
import com.scalefulfill.prediction.optimization.model.AllocationModels.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OptimizationSolversTest {

    private GreedyAllocationSolver greedySolver;
    private MilpAllocationSolver milpSolver;
    private OptimizationService optimizationService;

    @BeforeEach
    void setUp() {
        greedySolver = new GreedyAllocationSolver();
        milpSolver = new MilpAllocationSolver();
        optimizationService = new OptimizationService(greedySolver, milpSolver,
                new com.scalefulfill.prediction.metrics.PredictionMetrics(new SimpleMeterRegistry()));
    }

    private AllocationRequest createTestBatch(int orderCount) {
        // 2 FCs:
        // FC-1 (lat: 40.0, lon: -74.0, capacity: 50, cost: 5.0 base + 0.1/km, inv: PROD-1=100)
        // FC-2 (lat: 34.0, lon: -118.0, capacity: 50, cost: 4.0 base + 0.1/km, inv: PROD-1=100)
        AllocationFc fc1 = AllocationFc.builder()
                .fcId("FC-EAST")
                .name("East FC")
                .latitude(40.0)
                .longitude(-74.0)
                .maxCapacity(50)
                .baseCost(5.0)
                .costPerKm(0.08)
                .availableInventory(Map.of("PROD-1", 100))
                .build();

        AllocationFc fc2 = AllocationFc.builder()
                .fcId("FC-WEST")
                .name("West FC")
                .latitude(34.0)
                .longitude(-118.0)
                .maxCapacity(50)
                .baseCost(4.5)
                .costPerKm(0.08)
                .availableInventory(Map.of("PROD-1", 100))
                .build();

        List<AllocationOrder> orders = new ArrayList<>();
        for (int i = 1; i <= orderCount; i++) {
            // Half orders near East, half near West
            double lat = (i % 2 == 0) ? 40.7 : 34.1;
            double lon = (i % 2 == 0) ? -73.9 : -118.2;
            orders.add(AllocationOrder.builder()
                    .orderId("ORD-TEST-" + i)
                    .customerId("CUST-" + i)
                    .latitude(lat)
                    .longitude(lon)
                    .itemDemands(Map.of("PROD-1", 1))
                    .build());
        }

        return AllocationRequest.builder()
                .batchId("BATCH-TEST-1")
                .orders(orders)
                .fulfillmentCenters(List.of(fc1, fc2))
                .solverTimeoutMs(2000)
                .imbalancePenaltyWeight(0.20)
                .build();
    }

    @Test
    @DisplayName("Greedy solver allocates all orders to feasible FCs")
    void testGreedySolver() {
        AllocationRequest req = createTestBatch(10);
        AllocationResult result = greedySolver.solve(req);

        assertEquals("FEASIBLE", result.getStatus());
        assertEquals(10, result.getAllocatedCount());
        assertEquals(0, result.getUnallocatedCount());
        assertTrue(result.getTotalCost() > 0);
        assertTrue(result.getSolveTimeMs() >= 0);
    }

    @Test
    @DisplayName("OR-Tools MILP solver finds optimal or feasible assignment with optimality gap")
    void testMilpSolver() {
        AllocationRequest req = createTestBatch(10);
        AllocationResult result = milpSolver.solve(req);

        assertTrue("OPTIMAL".equals(result.getStatus()) || "FEASIBLE".equals(result.getStatus()),
                "MILP status should be OPTIMAL or FEASIBLE, got: " + result.getStatus());
        assertEquals(10, result.getAllocatedCount());
        assertEquals(0, result.getUnallocatedCount());
        assertTrue(result.getTotalCost() > 0);
        assertNotNull(result.getBestBound());
    }

    @Test
    @DisplayName("OptimizationService compareSolvers produces empirical comparison")
    void testCompareSolvers() {
        AllocationRequest req = createTestBatch(10);
        OptimizationService.SolverComparisonResult comp = optimizationService.compareSolvers(req);

        assertEquals(10, comp.getBatchSize());
        assertNotNull(comp.getGreedyResult());
        assertNotNull(comp.getMilpResult());
        assertTrue(comp.getMilpResult().getTotalCost() <= comp.getGreedyResult().getTotalCost() + 1e-4,
                "MILP cost should be <= Greedy cost");
    }
}
