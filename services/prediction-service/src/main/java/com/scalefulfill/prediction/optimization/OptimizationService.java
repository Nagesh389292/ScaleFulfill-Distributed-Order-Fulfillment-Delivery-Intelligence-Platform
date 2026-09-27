package com.scalefulfill.prediction.optimization;

import com.scalefulfill.prediction.metrics.PredictionMetrics;
import com.scalefulfill.prediction.optimization.model.AllocationModels.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class OptimizationService {

    private final GreedyAllocationSolver greedySolver;
    private final MilpAllocationSolver milpSolver;
    private final PredictionMetrics metrics;

    public AllocationResult solveGreedy(AllocationRequest request) {
        long start = System.currentTimeMillis();
        AllocationResult result = greedySolver.solve(request);
        metrics.recordSolveTime(Duration.ofMillis(System.currentTimeMillis() - start));
        return result;
    }

    public AllocationResult solveMilp(AllocationRequest request) {
        long start = System.currentTimeMillis();
        AllocationResult result = milpSolver.solve(request);
        metrics.recordSolveTime(Duration.ofMillis(System.currentTimeMillis() - start));
        return result;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SolverComparisonResult {
        private String batchId;
        private int batchSize;
        private AllocationResult greedyResult;
        private AllocationResult milpResult;
        private double costDifference;       // greedyCost - milpCost
        private double costSavingsPercentage; // (costDifference / greedyCost) * 100
        private double solveTimeRatio;       // milpTime / greedyTime
        private double loadBalanceImprovement; // greedyStdDev - milpStdDev
    }

    public SolverComparisonResult compareSolvers(AllocationRequest request) {
        AllocationResult greedy = solveGreedy(request);
        AllocationResult milp = solveMilp(request);

        double diff = greedy.getTotalCost() - milp.getTotalCost();
        double pct = greedy.getTotalCost() > 0 ? (diff / greedy.getTotalCost()) * 100.0 : 0.0;
        double timeRatio = greedy.getSolveTimeMs() > 0 ? milp.getSolveTimeMs() / greedy.getSolveTimeMs() : 1.0;
        double balImprovement = greedy.getCapacityUtilizationStdDev() - milp.getCapacityUtilizationStdDev();

        return SolverComparisonResult.builder()
                .batchId(request.getBatchId())
                .batchSize(request.getOrders().size())
                .greedyResult(greedy)
                .milpResult(milp)
                .costDifference(Math.round(diff * 100.0) / 100.0)
                .costSavingsPercentage(Math.round(pct * 100.0) / 100.0)
                .solveTimeRatio(Math.round(timeRatio * 10.0) / 10.0)
                .loadBalanceImprovement(Math.round(balImprovement * 100.0) / 100.0)
                .build();
    }
}
