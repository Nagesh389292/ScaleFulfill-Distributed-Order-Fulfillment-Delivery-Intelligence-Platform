package com.scalefulfill.inventory.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer metric definitions for inventory-service.
 *
 * <p>Kafka consumer lag is measured via AdminClient polling (every 15 s) rather
 * than listener callbacks. This gives the real committed-offset vs log-end-offset
 * delta — the most accurate lag signal available without a dedicated Kafka
 * exporter. It is the primary metric for the controlled-failure scenario.</p>
 */
@Component
@Getter
@Slf4j
public class InventoryMetrics {

    private static final String CONSUMER_GROUP = "inventory-service-group";
    private static final String TOPIC = "order.events.created";

    // Partition-level lag values — updated by the scheduled poller
    private final ConcurrentHashMap<Integer, AtomicLong> partitionLag = new ConcurrentHashMap<>();

    // Counters
    private final Counter reservationSuccessCounter;
    private final Counter reservationInsufficientStockCounter;
    private final Counter reservationDuplicateSkippedCounter;
    private final Counter reservationErrorCounter;

    // Timer
    private final Timer reservationTimerReserved;
    private final Timer reservationTimerInsufficient;
    private final Timer reservationTimerDuplicateSkipped;
    private final Timer reservationTimerError;

    private final AdminClient adminClient;
    private final MeterRegistry registry;

    @Value("${spring.kafka.consumer.group-id:inventory-service-group}")
    private String consumerGroup;

    public InventoryMetrics(MeterRegistry registry, AdminClient adminClient) {
        this.registry = registry;
        this.adminClient = adminClient;

        // Counters
        this.reservationSuccessCounter = Counter.builder("scalefulfill_inventory_reservation_total")
                .description("Inventory reservation attempts by result")
                .tag("result", "reserved")
                .register(registry);

        this.reservationInsufficientStockCounter = Counter.builder("scalefulfill_inventory_reservation_total")
                .description("Inventory reservation attempts by result")
                .tag("result", "insufficient_stock")
                .register(registry);

        this.reservationDuplicateSkippedCounter = Counter.builder("scalefulfill_inventory_reservation_total")
                .description("Inventory reservation attempts by result")
                .tag("result", "duplicate_skipped")
                .register(registry);

        this.reservationErrorCounter = Counter.builder("scalefulfill_inventory_reservation_total")
                .description("Inventory reservation attempts by result")
                .tag("result", "error")
                .register(registry);

        // Histograms — SLO buckets configured in application.yml
        this.reservationTimerReserved = Timer.builder("scalefulfill_inventory_reservation_duration")
                .description("Duration of Kafka consumer -> DB reservation transaction")
                .tag("result", "reserved")
                .publishPercentileHistogram(true)
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(2))
                .register(registry);

        this.reservationTimerInsufficient = Timer.builder("scalefulfill_inventory_reservation_duration")
                .description("Duration of Kafka consumer -> DB reservation transaction")
                .tag("result", "insufficient_stock")
                .publishPercentileHistogram(true)
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(2))
                .register(registry);

        this.reservationTimerDuplicateSkipped = Timer.builder("scalefulfill_inventory_reservation_duration")
                .description("Duration of Kafka consumer -> DB reservation transaction")
                .tag("result", "duplicate_skipped")
                .publishPercentileHistogram(true)
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(2))
                .register(registry);

        this.reservationTimerError = Timer.builder("scalefulfill_inventory_reservation_duration")
                .description("Duration of Kafka consumer -> DB reservation transaction")
                .tag("result", "error")
                .publishPercentileHistogram(true)
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(2))
                .register(registry);

        // Seed lag gauges for known partitions 0-3 (topics created with 4 partitions by default)
        for (int p = 0; p < 4; p++) {
            final int partition = p;
            AtomicLong lagValue = new AtomicLong(0);
            partitionLag.put(partition, lagValue);
            Gauge.builder("scalefulfill_inventory_kafka_consumer_lag", lagValue, AtomicLong::get)
                    .description("Kafka consumer lag for inventory-service-group on order.events.created")
                    .tag("topic", TOPIC)
                    .tag("partition", String.valueOf(partition))
                    .register(registry);
        }
    }

    /**
     * Polls Kafka AdminClient every 15 seconds to compute per-partition consumer lag.
     * Lag = log-end-offset - committed-consumer-offset.
     * On Kafka unavailability the previous value is retained.
     */
    @Scheduled(fixedDelay = 15000, initialDelay = 10000)
    public void refreshKafkaConsumerLag() {
        try {
            // Step 1: get committed offsets for the consumer group
            ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(CONSUMER_GROUP);
            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> committedOffsets =
                    offsetsResult.partitionsToOffsetAndMetadata().get(10, java.util.concurrent.TimeUnit.SECONDS);

            if (committedOffsets == null || committedOffsets.isEmpty()) {
                return; // Consumer group hasn't committed yet (no lag data available)
            }

            // Step 2: get log-end-offsets for the same partitions
            Map<TopicPartition, OffsetSpec> partitionOffsetSpecs = new HashMap<>();
            for (TopicPartition tp : committedOffsets.keySet()) {
                if (TOPIC.equals(tp.topic())) {
                    partitionOffsetSpecs.put(tp, OffsetSpec.latest());
                }
            }

            if (partitionOffsetSpecs.isEmpty()) {
                return;
            }

            Map<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> endOffsets =
                    adminClient.listOffsets(partitionOffsetSpecs).all()
                            .get(10, java.util.concurrent.TimeUnit.SECONDS);

            // Step 3: compute lag per partition
            for (Map.Entry<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> entry : endOffsets.entrySet()) {
                TopicPartition tp = entry.getKey();
                long endOffset = entry.getValue().offset();
                long committedOffset = committedOffsets.getOrDefault(tp,
                        new org.apache.kafka.clients.consumer.OffsetAndMetadata(0)).offset();
                long lag = Math.max(0, endOffset - committedOffset);

                AtomicLong lagValue = partitionLag.computeIfAbsent(tp.partition(), p -> {
                    AtomicLong newLag = new AtomicLong(0);
                    Gauge.builder("scalefulfill_inventory_kafka_consumer_lag", newLag, AtomicLong::get)
                            .description("Kafka consumer lag for inventory-service-group on order.events.created")
                            .tag("topic", TOPIC)
                            .tag("partition", String.valueOf(tp.partition()))
                            .register(registry);
                    return newLag;
                });
                lagValue.set(lag);
            }

            log.debug("[inventory-metrics] Kafka consumer lag refreshed for group [{}]", CONSUMER_GROUP);
        } catch (Exception e) {
            log.warn("[inventory-metrics] Failed to refresh Kafka consumer lag: {}", e.getMessage());
        }
    }
}
