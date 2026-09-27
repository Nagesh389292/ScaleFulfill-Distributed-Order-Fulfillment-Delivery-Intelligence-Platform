package com.scalefulfill.prediction.optimization;

import com.google.ortools.Loader;
import com.google.ortools.linearsolver.MPConstraint;
import com.google.ortools.linearsolver.MPObjective;
import com.google.ortools.linearsolver.MPSolver;
import com.google.ortools.linearsolver.MPVariable;
import com.scalefulfill.prediction.optimization.model.AllocationModels.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Mixed-Integer Linear Programming (MILP) Solver using Google OR-Tools (SCIP engine).
 * Formulates the multi-facility assignment problem with strict inventory capacity,
 * dispatch throughput limits, delivery distance optimization, and workload balancing.
 */
@Slf4j
@Component
public class MilpAllocationSolver {

    static {
        try {
            Loader.loadNativeLibraries();
            log.info("[milp-solver] Google OR-Tools native libraries loaded successfully.");
        } catch (Throwable t) {
            log.error("[milp-solver] Failed to initialize Google OR-Tools native binaries: {}", t.getMessage(), t);
        }
    }

    private static final double UNALLOCATED_PENALTY = 5000.0; // Penalty cost for unfulfilled order

    public AllocationResult solve(AllocationRequest request) {
        long startTime = System.nanoTime();
        List<AllocationOrder> orders = request.getOrders();
        List<AllocationFc> fcs = request.getFulfillmentCenters();
        int nOrders = orders.size();
        int nFcs = fcs.size();

        MPSolver solver = MPSolver.createSolver("SCIP");
        if (solver == null) {
            log.error("[milp-solver] SCIP solver unavailable in OR-Tools environment.");
            throw new IllegalStateException("SCIP solver backend not available in OR-Tools");
        }

        // Apply configurable solver timeout
        long timeoutMs = request.getSolverTimeoutMs() > 0 ? request.getSolverTimeoutMs() : 3000;
        solver.setTimeLimit(timeoutMs);

        // 1. Decision Variables:
        // x[i][j] in {0, 1}: order i assigned to FC j
        // u[i] in [0, 1]: order i unallocated slack penalty
        MPVariable[][] x = new MPVariable[nOrders][nFcs];
        MPVariable[] u = new MPVariable[nOrders];

        for (int i = 0; i < nOrders; i++) {
            u[i] = solver.makeNumVar(0.0, 1.0, "u_" + i);
            for (int j = 0; j < nFcs; j++) {
                x[i][j] = solver.makeBoolVar("x_" + i + "_" + j);
            }
        }

        // 2. Imbalance Variables:
        // d[j] >= 0: positive deviation from average load
        double targetLoad = (double) nOrders / Math.max(nFcs, 1);
        MPVariable[] dev = new MPVariable[nFcs];
        for (int j = 0; j < nFcs; j++) {
            dev[j] = solver.makeNumVar(0.0, (double) nOrders, "dev_" + j);
        }

        // 3. Constraints:
        // Constraint 3a: Each order assigned to exactly one FC (or marked unallocated via slack)
        for (int i = 0; i < nOrders; i++) {
            MPConstraint assignConstraint = solver.makeConstraint(1.0, 1.0, "assign_" + i);
            assignConstraint.setCoefficient(u[i], 1.0);
            for (int j = 0; j < nFcs; j++) {
                assignConstraint.setCoefficient(x[i][j], 1.0);
            }
        }

        // Constraint 3b: Dispatch capacity constraint per FC: sum_i x[i][j] <= C_j
        for (int j = 0; j < nFcs; j++) {
            AllocationFc fc = fcs.get(j);
            MPConstraint capConstraint = solver.makeConstraint(0.0, (double) fc.getMaxCapacity(), "cap_" + j);
            for (int i = 0; i < nOrders; i++) {
                capConstraint.setCoefficient(x[i][j], 1.0);
            }
        }

        // Constraint 3c: Inventory availability constraint per SKU k and FC j:
        // sum_i (demand_ik * x[i][j]) <= availableStock_jk
        Set<String> allSkus = new HashSet<>();
        for (AllocationOrder o : orders) {
            if (o.getItemDemands() != null) {
                allSkus.addAll(o.getItemDemands().keySet());
            }
        }

        for (int j = 0; j < nFcs; j++) {
            AllocationFc fc = fcs.get(j);
            Map<String, Integer> stock = fc.getAvailableInventory() != null ? fc.getAvailableInventory() : Collections.emptyMap();

            for (String sku : allSkus) {
                int available = stock.getOrDefault(sku, 0);
                MPConstraint invConstraint = solver.makeConstraint(0.0, (double) available, "inv_" + j + "_" + sku);
                for (int i = 0; i < nOrders; i++) {
                    int demand = orders.get(i).getItemDemands() != null ?
                            orders.get(i).getItemDemands().getOrDefault(sku, 0) : 0;
                    if (demand > 0) {
                        invConstraint.setCoefficient(x[i][j], demand);
                    }
                }
            }
        }

        // Constraint 3d: Workload balance linear deviation: dev[j] >= sum_i x[i][j] - targetLoad
        for (int j = 0; j < nFcs; j++) {
            MPConstraint devConstraint = solver.makeConstraint(-targetLoad, Double.POSITIVE_INFINITY, "dev_bound_" + j);
            devConstraint.setCoefficient(dev[j], 1.0);
            for (int i = 0; i < nOrders; i++) {
                devConstraint.setCoefficient(x[i][j], -1.0);
            }
        }

        // 4. Objective Function:
        // Minimize: sum_{i,j} cost(i,j)*x[i][j] + sum_i UNALLOCATED_PENALTY*u[i] + lambda * sum_j dev[j]
        MPObjective objective = solver.objective();
        objective.setMinimization();

        double lambda = request.getImbalancePenaltyWeight() > 0 ? request.getImbalancePenaltyWeight() : 0.50;

        for (int i = 0; i < nOrders; i++) {
            objective.setCoefficient(u[i], UNALLOCATED_PENALTY);
            AllocationOrder order = orders.get(i);
            for (int j = 0; j < nFcs; j++) {
                AllocationFc fc = fcs.get(j);
                double shippingCost = OptimizationCostUtil.calculateShippingCost(order, fc);
                objective.setCoefficient(x[i][j], shippingCost);
            }
        }

        for (int j = 0; j < nFcs; j++) {
            objective.setCoefficient(dev[j], lambda);
        }

        // 5. Execute Solver
        MPSolver.ResultStatus resultStatus = solver.solve();
        long elapsedNs = System.nanoTime() - startTime;
        double solveTimeMs = elapsedNs / 1_000_000.0;

        String statusStr;
        switch (resultStatus) {
            case OPTIMAL -> statusStr = "OPTIMAL";
            case FEASIBLE -> statusStr = "FEASIBLE";
            case INFEASIBLE -> statusStr = "INFEASIBLE";
            case UNBOUNDED -> statusStr = "UNBOUNDED";
            default -> statusStr = "TIMEOUT";
        }

        List<OrderAssignment> assignments = new ArrayList<>();
        Map<String, Integer> fcUtilization = new HashMap<>();
        for (AllocationFc fc : fcs) {
            fcUtilization.put(fc.getFcId(), 0);
        }

        int unallocated = 0;
        double actualCost = 0.0;

        if (resultStatus == MPSolver.ResultStatus.OPTIMAL || resultStatus == MPSolver.ResultStatus.FEASIBLE) {
            for (int i = 0; i < nOrders; i++) {
                AllocationOrder order = orders.get(i);
                boolean assigned = false;
                for (int j = 0; j < nFcs; j++) {
                    if (x[i][j].solutionValue() > 0.5) {
                        AllocationFc fc = fcs.get(j);
                        double distanceKm = OptimizationCostUtil.haversineDistanceKm(
                                order.getLatitude(), order.getLongitude(),
                                fc.getLatitude(), fc.getLongitude());
                        double cost = OptimizationCostUtil.calculateShippingCost(order, fc);

                        actualCost += cost;
                        fcUtilization.put(fc.getFcId(), fcUtilization.get(fc.getFcId()) + 1);

                        assignments.add(OrderAssignment.builder()
                                .orderId(order.getOrderId())
                                .assignedFcId(fc.getFcId())
                                .distanceKm(Math.round(distanceKm * 10.0) / 10.0)
                                .shippingCost(Math.round(cost * 100.0) / 100.0)
                                .fulfillmentCost(fc.getBaseCost())
                                .totalCost(Math.round(cost * 100.0) / 100.0)
                                .build());
                        assigned = true;
                        break;
                    }
                }
                if (!assigned) {
                    unallocated++;
                }
            }
        } else {
            unallocated = nOrders;
        }

        double bestBound = solver.objective().bestBound();
        double objValue = solver.objective().value();
        double gap = 0.0;
        if (Math.abs(objValue) > 1e-6) {
            gap = Math.abs(objValue - bestBound) / Math.abs(objValue) * 100.0;
        }

        double stdDev = OptimizationCostUtil.calculateStdDev(fcUtilization.values());

        return AllocationResult.builder()
                .batchId(request.getBatchId())
                .algorithm("OR_TOOLS_MILP")
                .status(statusStr)
                .batchSize(nOrders)
                .allocatedCount(assignments.size())
                .unallocatedCount(unallocated)
                .totalCost(Math.round(actualCost * 100.0) / 100.0)
                .solveTimeMs(Math.round(solveTimeMs * 100.0) / 100.0)
                .optimalityGap(Math.round(gap * 100.0) / 100.0)
                .bestBound(Double.isFinite(bestBound) ? Math.round(bestBound * 100.0) / 100.0 : null)
                .fcUtilization(fcUtilization)
                .capacityUtilizationStdDev(Math.round(stdDev * 100.0) / 100.0)
                .assignments(assignments)
                .build();
    }
}
