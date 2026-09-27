package com.scalefulfill.order.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Centralised Micrometer metric definitions for order-service.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>All metrics registered eagerly at construction time — they appear in
 *       /actuator/prometheus with value 0.0 immediately, which prevents "no data"
 *       gaps in Grafana dashboards before any load arrives.</li>
 *   <li>Label cardinality is bounded: {@code status} ∈ {success, validation_error,
 *       inventory_unavailable, internal_error}. No order_id or customer_id labels.</li>
 *   <li>Histogram buckets are tuned to the observed Phase 1/3 latency ranges
 *       (2 ms happy-path P50, 80 ms P95 NFR target).</li>
 * </ul>
 * </p>
 */
@Component
@Getter
public class OrderMetrics {

    // --- Counters ---

    /** Total orders successfully committed to DB + outbox. */
    private final Counter ordersCreatedSuccess;

    /** Orders rejected due to insufficient inventory (business rejection). */
    private final Counter ordersCreatedInventoryUnavailable;

    /** Orders rejected due to request validation errors (bad input). */
    private final Counter ordersCreatedValidationError;

    /** Orders that triggered an unhandled internal error. */
    private final Counter ordersCreatedInternalError;

    /** Outbox events successfully published to Kafka by the outbox poller. */
    private final Counter outboxEventsPublishedSuccess;

    /** Outbox events that failed after Kafka send retries (DLQ candidates). */
    private final Counter outboxEventsPublishedFailure;

    // --- Timers (Histograms) ---

    /**
     * End-to-end duration of createOrderWithOutbox() — from request entry to
     * successful DB+outbox commit. Maps to NFR: P95 ≤ 80 ms.
     * Buckets: 5, 10, 25, 50, 100, 200, 500 ms, 1 s.
     */
    private final Timer orderProcessingTimerSuccess;
    private final Timer orderProcessingTimerError;

    public OrderMetrics(MeterRegistry registry) {
        // Counters
        this.ordersCreatedSuccess = Counter.builder("scalefulfill_orders_created_total")
                .description("Total order creation attempts by outcome")
                .tag("status", "success")
                .register(registry);

        this.ordersCreatedInventoryUnavailable = Counter.builder("scalefulfill_orders_created_total")
                .description("Total order creation attempts by outcome")
                .tag("status", "inventory_unavailable")
                .register(registry);

        this.ordersCreatedValidationError = Counter.builder("scalefulfill_orders_created_total")
                .description("Total order creation attempts by outcome")
                .tag("status", "validation_error")
                .register(registry);

        this.ordersCreatedInternalError = Counter.builder("scalefulfill_orders_created_total")
                .description("Total order creation attempts by outcome")
                .tag("status", "internal_error")
                .register(registry);

        this.outboxEventsPublishedSuccess = Counter.builder("scalefulfill_outbox_events_published_total")
                .description("Outbox events published to Kafka by result")
                .tag("result", "success")
                .register(registry);

        this.outboxEventsPublishedFailure = Counter.builder("scalefulfill_outbox_events_published_total")
                .description("Outbox events published to Kafka by result")
                .tag("result", "failure")
                .register(registry);

        // Histograms — SLO buckets defined in application.yml via management.metrics.distribution.slo
        this.orderProcessingTimerSuccess = Timer.builder("scalefulfill_order_processing_duration")
                .description("End-to-end order processing duration (DB + outbox commit)")
                .tag("status", "success")
                .publishPercentileHistogram(true)
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(5))
                .register(registry);

        this.orderProcessingTimerError = Timer.builder("scalefulfill_order_processing_duration")
                .description("End-to-end order processing duration (DB + outbox commit)")
                .tag("status", "error")
                .publishPercentileHistogram(true)
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(5))
                .register(registry);
    }
}
