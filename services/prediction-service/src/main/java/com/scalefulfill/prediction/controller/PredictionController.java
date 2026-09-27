package com.scalefulfill.prediction.controller;

import com.scalefulfill.prediction.model.DeliveryPrediction;
import com.scalefulfill.prediction.model.OrderPredictionEvent;
import com.scalefulfill.prediction.pool.PredictionWorkerPool;
import com.scalefulfill.prediction.repository.PredictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
public class PredictionController {

    private final PredictionRepository repository;
    private final PredictionWorkerPool workerPool;

    @GetMapping("/{orderId}")
    public ResponseEntity<DeliveryPrediction> getPrediction(@PathVariable String orderId) {
        return repository.findByOrderId(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public CompletableFuture<ResponseEntity<DeliveryPrediction>> createPrediction(
            @RequestBody OrderPredictionEvent event) {
        return workerPool.submitPredictionTask(event)
                .thenApply(p -> ResponseEntity.status(HttpStatus.CREATED).body(p));
    }

    @GetMapping
    public ResponseEntity<Collection<DeliveryPrediction>> getAllPredictions() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
                "totalPredictions", repository.size(),
                "staleAccessCount", repository.getStaleAccessCount(),
                "activeWorkers", workerPool.getExecutor().getActiveCount(),
                "corePoolSize", workerPool.getExecutor().getCorePoolSize(),
                "queueDepth", workerPool.getExecutor().getQueue().size(),
                "queueRemaining", workerPool.getExecutor().getQueue().remainingCapacity()
        ));
    }

    @PostMapping("/workers")
    public ResponseEntity<Map<String, Object>> reconfigureWorkers(@RequestParam int size) {
        if (size <= 0 || size > 64) {
            return ResponseEntity.badRequest().body(Map.of("error", "Size must be between 1 and 64"));
        }
        workerPool.reconfigureWorkers(size);
        return ResponseEntity.ok(Map.of(
                "message", "Worker pool reconfigured",
                "newWorkerCount", size
        ));
    }
}
