package com.scalefulfill.prediction;

import com.scalefulfill.prediction.calculator.DemandVelocityCalculator;
import com.scalefulfill.prediction.calculator.EtaPredictor;
import com.scalefulfill.prediction.calculator.FeatureCalculator;
import com.scalefulfill.prediction.metrics.PredictionMetrics;
import com.scalefulfill.prediction.model.DeliveryPrediction;
import com.scalefulfill.prediction.model.OrderPredictionEvent;
import com.scalefulfill.prediction.pool.PredictionWorkerPool;
import com.scalefulfill.prediction.repository.FulfillmentCenterRegistry;
import com.scalefulfill.prediction.repository.PredictionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class PredictionWorkerPoolTest {

    private PredictionWorkerPool workerPool;
    private PredictionRepository repository;

    @BeforeEach
    void setUp() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PredictionMetrics metrics = new PredictionMetrics(registry);
        FulfillmentCenterRegistry fcRegistry = new FulfillmentCenterRegistry();
        FeatureCalculator featureCalculator = new FeatureCalculator();
        EtaPredictor etaPredictor = new EtaPredictor();
        DemandVelocityCalculator demandVelocityCalculator = new DemandVelocityCalculator();
        repository = new PredictionRepository();

        workerPool = new PredictionWorkerPool(
                2, // coreSize
                4, // maxSize
                100, // queueCapacity
                metrics,
                fcRegistry,
                featureCalculator,
                etaPredictor,
                demandVelocityCalculator,
                repository
        );
    }

    @AfterEach
    void tearDown() {
        workerPool.shutdown();
    }

    @Test
    @DisplayName("Worker pool asynchronously executes and stores predictions")
    void testConcurrentWorkerExecution() throws ExecutionException, InterruptedException {
        int count = 20;
        List<CompletableFuture<DeliveryPrediction>> futures = new ArrayList<>();

        for (int i = 1; i <= count; i++) {
            OrderPredictionEvent event = OrderPredictionEvent.builder()
                    .orderId("ORD-CONCUR-" + i)
                    .customerId("CUST-1001")
                    .totalAmount(BigDecimal.valueOf(50.0 * i))
                    .items(List.of(
                            OrderPredictionEvent.PredictionItem.builder()
                                    .productId("PROD-1001")
                                    .quantity(2)
                                    .unitPrice(BigDecimal.valueOf(25.0))
                                    .weightKg(0.8)
                                    .build()
                    ))
                    .build();
            futures.add(workerPool.submitPredictionTask(event));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        assertEquals(count, repository.size(), "All 20 predictions should be computed and stored");
        for (int i = 1; i <= count; i++) {
            assertTrue(repository.findByOrderId("ORD-CONCUR-" + i).isPresent());
            DeliveryPrediction p = repository.findByOrderId("ORD-CONCUR-" + i).get();
            assertNotNull(p.getPredictedEtaMinutes());
            assertNotNull(p.getDemandVelocityScore());
            assertEquals("eta-v1", p.getModelVersion());
            assertFalse(p.isStale());
        }
    }

    @Test
    @DisplayName("Worker pool dynamically reconfigures worker threads")
    void testReconfigureWorkers() {
        assertEquals(2, workerPool.getExecutor().getCorePoolSize());
        workerPool.reconfigureWorkers(8);
        assertEquals(8, workerPool.getExecutor().getCorePoolSize());
        assertEquals(8, workerPool.getExecutor().getMaximumPoolSize());
    }
}
