package com.scalefulfill.prediction.pool;

import com.scalefulfill.prediction.calculator.DemandVelocityCalculator;
import com.scalefulfill.prediction.calculator.EtaPredictor;
import com.scalefulfill.prediction.calculator.FeatureCalculator;
import com.scalefulfill.prediction.metrics.PredictionMetrics;
import com.scalefulfill.prediction.model.CustomerLocation;
import com.scalefulfill.prediction.model.DeliveryPrediction;
import com.scalefulfill.prediction.model.FulfillmentCenter;
import com.scalefulfill.prediction.model.OrderPredictionEvent;
import com.scalefulfill.prediction.repository.FulfillmentCenterRegistry;
import com.scalefulfill.prediction.repository.PredictionRepository;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded Worker Pool with explicit backpressure, queue tracking, and Micrometer telemetry.
 */
@Slf4j
@Component
public class PredictionWorkerPool {

    @Getter
    private final ThreadPoolExecutor executor;
    private final PredictionMetrics metrics;
    private final FulfillmentCenterRegistry fcRegistry;
    private final FeatureCalculator featureCalculator;
    private final EtaPredictor etaPredictor;
    private final DemandVelocityCalculator demandVelocityCalculator;
    private final PredictionRepository predictionRepository;
    private final AtomicInteger defaultComputeIntensity = new AtomicInteger(0);

    @Value("${scalefulfill.prediction.ttl-minutes:15}")
    private long predictionTtlMinutes = 15;

    public PredictionWorkerPool(
            @Value("${scalefulfill.prediction.pool.core-size:4}") int coreSize,
            @Value("${scalefulfill.prediction.pool.max-size:8}") int maxSize,
            @Value("${scalefulfill.prediction.pool.queue-capacity:1000}") int queueCapacity,
            PredictionMetrics metrics,
            FulfillmentCenterRegistry fcRegistry,
            FeatureCalculator featureCalculator,
            EtaPredictor etaPredictor,
            DemandVelocityCalculator demandVelocityCalculator,
            PredictionRepository predictionRepository
    ) {
        this.metrics = metrics;
        this.fcRegistry = fcRegistry;
        this.featureCalculator = featureCalculator;
        this.etaPredictor = etaPredictor;
        this.demandVelocityCalculator = demandVelocityCalculator;
        this.predictionRepository = predictionRepository;

        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(queueCapacity);
        AtomicInteger threadId = new AtomicInteger(1);
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "prediction-worker-" + threadId.getAndIncrement());
            t.setDaemon(true);
            return t;
        };

        RejectedExecutionHandler rejectionHandler = (r, exec) -> {
            metrics.recordRejected();
            metrics.updateThreadPoolGauges(exec);
            log.warn("[worker-pool] Prediction queue saturated (depth={}/{}). Backpressure rejecting task.",
                    exec.getQueue().size(), queueCapacity);
            throw new RejectedExecutionException("Prediction worker pool queue capacity exceeded");
        };

        this.executor = new ThreadPoolExecutor(
                coreSize,
                maxSize,
                60L,
                TimeUnit.SECONDS,
                workQueue,
                threadFactory,
                rejectionHandler
        );
        metrics.updateThreadPoolGauges(this.executor);
    }

    /**
     * Submit an asynchronous prediction task to the bounded pool.
     */
    public CompletableFuture<DeliveryPrediction> submitPredictionTask(OrderPredictionEvent event) {
        metrics.updateThreadPoolGauges(executor);
        return CompletableFuture.supplyAsync(() -> executePrediction(event), executor)
                .whenComplete((res, ex) -> metrics.updateThreadPoolGauges(executor));
    }

    /**
     * Executes feature extraction, kinematic ETA estimation, and demand scoring.
     */
    public DeliveryPrediction executePrediction(OrderPredictionEvent event) {
        Instant start = Instant.now();
        try {
            CustomerLocation customer = fcRegistry.getCustomerLocation(event.getCustomerId());

            // Select optimal FC from candidates or nearest feasible FC
            List<String> candidates = event.getCandidateFulfillmentCenters();
            FulfillmentCenter chosenFc;
            if (candidates != null && !candidates.isEmpty()) {
                chosenFc = candidates.stream()
                        .map(fcRegistry::getFulfillmentCenter)
                        .filter(java.util.Objects::nonNull)
                        .min(Comparator.comparingDouble(fc -> featureCalculator.calculateDistanceKm(customer, fc)))
                        .orElseGet(() -> fcRegistry.getAllFulfillmentCenters().iterator().next());
            } else {
                chosenFc = fcRegistry.getAllFulfillmentCenters().stream()
                        .min(Comparator.comparingDouble(fc -> featureCalculator.calculateDistanceKm(customer, fc)))
                        .orElseThrow();
            }

            double distanceKm = featureCalculator.calculateDistanceKm(customer, chosenFc);
            double velocityKmh = featureCalculator.estimateCarrierVelocityKmh(distanceKm);
            double weightKg = featureCalculator.calculateTotalWeightKg(event);

            Map<String, Object> featureVector = featureCalculator.extractFeatures(
                    customer, chosenFc, event, distanceKm, velocityKmh, weightKg);

            int intensity = (event.getComputeIntensity() != null && event.getComputeIntensity() > 0)
                    ? event.getComputeIntensity()
                    : defaultComputeIntensity.get();

            double predictedEta = (intensity > 0)
                    ? etaPredictor.simulateHeavyEtaCalculation(distanceKm, velocityKmh, chosenFc, intensity)
                    : etaPredictor.predictEtaMinutes(distanceKm, velocityKmh, chosenFc);

            double demandVelocity = demandVelocityCalculator.calculateVelocityScore(event);

            Instant generatedAt = Instant.now();
            Instant expiresAt = generatedAt.plus(Duration.ofMinutes(predictionTtlMinutes));

            DeliveryPrediction prediction = DeliveryPrediction.builder()
                    .predictionId("PRED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .orderId(event.getOrderId())
                    .customerId(event.getCustomerId())
                    .assignedFcId(chosenFc.getId())
                    .modelVersion(EtaPredictor.MODEL_VERSION)
                    .predictedEtaMinutes(predictedEta)
                    .demandVelocityScore(demandVelocity)
                    .featureVector(featureVector)
                    .generatedAt(generatedAt)
                    .expiresAt(expiresAt)
                    .stale(false)
                    .build();

            predictionRepository.save(prediction);
            metrics.recordSuccess();
            metrics.recordProcessingTime(Duration.between(start, Instant.now()));

            log.debug("[worker-pool] Order {} predicted: ETA={}m, DemandScore={}, FC={}, Duration={}ms",
                    event.getOrderId(), predictedEta, demandVelocity, chosenFc.getId(),
                    Duration.between(start, Instant.now()).toMillis());

            return prediction;
        } catch (Exception e) {
            metrics.recordFailure();
            log.error("[worker-pool] Prediction failed for order {}: {}", event.getOrderId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Dynamically resize pool workers for empirical benchmarking.
     */
    public synchronized void reconfigureWorkers(int numWorkers) {
        log.info("[worker-pool] Reconfiguring worker threads to: {}", numWorkers);
        if (numWorkers > executor.getMaximumPoolSize()) {
            executor.setMaximumPoolSize(numWorkers);
            executor.setCorePoolSize(numWorkers);
        } else {
            executor.setCorePoolSize(numWorkers);
            executor.setMaximumPoolSize(numWorkers);
        }
        metrics.updateThreadPoolGauges(executor);
    }

    public void setDefaultComputeIntensity(int intensity) {
        log.info("[worker-pool] Configured default compute intensity iterations: {}", intensity);
        this.defaultComputeIntensity.set(Math.max(0, intensity));
    }

    public int getDefaultComputeIntensity() {
        return this.defaultComputeIntensity.get();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
