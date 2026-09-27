package com.scalefulfill.prediction.controller;

import com.scalefulfill.prediction.optimization.OptimizationService;
import com.scalefulfill.prediction.optimization.OptimizationService.SolverComparisonResult;
import com.scalefulfill.prediction.optimization.model.AllocationModels.AllocationRequest;
import com.scalefulfill.prediction.optimization.model.AllocationModels.AllocationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/optimize")
@RequiredArgsConstructor
public class OptimizationController {

    private final OptimizationService optimizationService;

    @PostMapping("/greedy")
    public ResponseEntity<AllocationResult> runGreedy(@RequestBody AllocationRequest request) {
        return ResponseEntity.ok(optimizationService.solveGreedy(request));
    }

    @PostMapping("/milp")
    public ResponseEntity<AllocationResult> runMilp(@RequestBody AllocationRequest request) {
        return ResponseEntity.ok(optimizationService.solveMilp(request));
    }

    @PostMapping("/compare")
    public ResponseEntity<SolverComparisonResult> compareSolvers(@RequestBody AllocationRequest request) {
        return ResponseEntity.ok(optimizationService.compareSolvers(request));
    }
}
