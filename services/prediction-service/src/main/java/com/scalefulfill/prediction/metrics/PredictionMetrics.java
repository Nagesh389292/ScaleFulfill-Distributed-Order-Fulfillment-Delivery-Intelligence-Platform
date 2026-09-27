package com.scalefulfill.prediction.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class PredictionMetrics {

    private final MeterRegistry registry;

    private final Counter successCounter;
    private final Counter failureCounter;
    private final Counter rejectedCounter;
    private final Counter staleCounter;
    private final Timer processingTimer;
    private final Timer solverTimer;

    private final AtomicInteger activeWorkers = new AtomicInteger(0);
    private final AtomicInteger queueDepth = new AtomicInteger(0);
    private final AtomicInteger queueRemaining = new AtomicInteger(0);
    private final AtomicInteger poolSize = new AtomicInteger(0);

    public PredictionMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.successCounter = Counter.builder("scalefulfill_prediction_total")
                .tag("status", "success")
                .description("Total successfully computed order predictions")
                .register(registry);

        this.failureCounter = Counter.builder("scalefulfill_prediction_total")
                .tag("status", "failure")
                .description("Total failed order predictions")
                .register(registry);

        this.rejectedCounter = Counter.builder("scalefulfill_prediction_total")
                .tag("status", "rejected")
                .description("Total predictions rejected by worker pool backpressure")
                .register(registry);

        this.staleCounter = Counter.builder("scalefulfill_prediction_stale_total")
                .description("Total stale/expired predictions detected")
                .register(registry);

        this.processingTimer = Timer.builder("scalefulfill_prediction_processing_duration_seconds")
                .description("End-to-end prediction execution duration across worker pool")
                .publishPercentiles(0.50, 0.90, 0.95, 0.99)
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(10))
                .register(registry);

        this.solverTimer = Timer.builder("scalefulfill_optimization_solve_duration_seconds")
                .description("Mathematical optimization solver duration")
                .publishPercentiles(0.50, 0.90, 0.95, 0.99)
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(30))
                .register(registry);

        // Register Gauges
        Gauge.builder("scalefulfill_prediction_workers_active", activeWorkers, AtomicInteger::get)
                .description("Number of active prediction worker threads")
                .register(registry);

        Gauge.builder("scalefulfill_prediction_queue_depth", queueDepth, AtomicInteger::get)
                .description("Current tasks waiting in prediction worker queue")
                .register(registry);

        Gauge.builder("scalefulfill_prediction_queue_remaining", queueRemaining, AtomicInteger::get)
                .description("Remaining capacity in prediction worker queue")
                .register(registry);

        Gauge.builder("scalefulfill_prediction_pool_size", poolSize, AtomicInteger::get)
                .description("Current configured size of prediction worker thread pool")
                .register(registry);
    }

    public void recordSuccess() {
        successCounter.increment();
    }

    public void recordFailure() {
        failureCounter.increment();
    }

    public void recordRejected() {
        rejectedCounter.increment();
    }

    public void recordStale() {
        staleCounter.increment();
    }

    public void recordProcessingTime(Duration duration) {
        processingTimer.record(duration);
    }

    public void recordSolveTime(Duration duration) {
        solverTimer.record(duration);
    }

    public void updateThreadPoolGauges(ThreadPoolExecutor executor) {
        if (executor != null) {
            activeWorkers.set(executor.getActiveCount());
            queueDepth.set(executor.getQueue().size());
            queueRemaining.set(executor.getQueue().remainingCapacity());
            poolSize.set(executor.getPoolSize());
        }
    }
}
