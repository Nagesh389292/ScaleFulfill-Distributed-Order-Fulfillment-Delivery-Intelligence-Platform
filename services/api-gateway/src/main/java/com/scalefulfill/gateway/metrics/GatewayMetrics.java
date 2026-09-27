package com.scalefulfill.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import org.springframework.stereotype.Component;

/**
 * Gateway-level Micrometer counters.
 *
 * <p>Spring Cloud Gateway auto-publishes {@code spring.cloud.gateway.requests}
 * via Micrometer once the registry is on the classpath. This bean adds a
 * supplementary counter that includes the {@code rate_limited} status label
 * which the built-in metric does not produce, giving a clear signal of how
 * many requests were rejected by the token-bucket filter.</p>
 *
 * <p>Label cardinality is bounded: route ∈ {order-service, inventory-service,
 * search-service, unknown} and status ∈ {2xx, 4xx, 5xx, rate_limited}.</p>
 */
@Component
@Getter
public class GatewayMetrics {

    private final Counter orderServiceRequests2xx;
    private final Counter orderServiceRequests4xx;
    private final Counter orderServiceRequests5xx;
    private final Counter orderServiceRateLimited;

    private final Counter inventoryServiceRequests2xx;
    private final Counter inventoryServiceRequests4xx;
    private final Counter inventoryServiceRequests5xx;

    private final Counter searchServiceRequests2xx;
    private final Counter searchServiceRequests4xx;
    private final Counter searchServiceRequests5xx;

    private final Counter totalRateLimited;

    public GatewayMetrics(MeterRegistry registry) {
        // Order service counters
        this.orderServiceRequests2xx = Counter.builder("scalefulfill_gateway_requests_total")
                .description("Gateway routed requests by downstream service and HTTP status class")
                .tag("route", "order-service").tag("status", "2xx").register(registry);
        this.orderServiceRequests4xx = Counter.builder("scalefulfill_gateway_requests_total")
                .tag("route", "order-service").tag("status", "4xx").register(registry);
        this.orderServiceRequests5xx = Counter.builder("scalefulfill_gateway_requests_total")
                .tag("route", "order-service").tag("status", "5xx").register(registry);
        this.orderServiceRateLimited = Counter.builder("scalefulfill_gateway_requests_total")
                .tag("route", "order-service").tag("status", "rate_limited").register(registry);

        // Inventory service counters
        this.inventoryServiceRequests2xx = Counter.builder("scalefulfill_gateway_requests_total")
                .tag("route", "inventory-service").tag("status", "2xx").register(registry);
        this.inventoryServiceRequests4xx = Counter.builder("scalefulfill_gateway_requests_total")
                .tag("route", "inventory-service").tag("status", "4xx").register(registry);
        this.inventoryServiceRequests5xx = Counter.builder("scalefulfill_gateway_requests_total")
                .tag("route", "inventory-service").tag("status", "5xx").register(registry);

        // Search service counters
        this.searchServiceRequests2xx = Counter.builder("scalefulfill_gateway_requests_total")
                .tag("route", "search-service").tag("status", "2xx").register(registry);
        this.searchServiceRequests4xx = Counter.builder("scalefulfill_gateway_requests_total")
                .tag("route", "search-service").tag("status", "4xx").register(registry);
        this.searchServiceRequests5xx = Counter.builder("scalefulfill_gateway_requests_total")
                .tag("route", "search-service").tag("status", "5xx").register(registry);

        // Total rate-limited across all routes (convenient for alert threshold)
        this.totalRateLimited = Counter.builder("scalefulfill_gateway_rate_limited_total")
                .description("Total requests rejected by gateway token-bucket rate limiter")
                .register(registry);
    }
}
