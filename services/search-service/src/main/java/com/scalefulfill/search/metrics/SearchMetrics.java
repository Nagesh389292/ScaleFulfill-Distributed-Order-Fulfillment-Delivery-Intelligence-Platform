package com.scalefulfill.search.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer metric definitions for search-service.
 *
 * <p>The search Kafka consumer lag gauge is the primary observable signal
 * during the controlled-failure scenario: stopping OpenSearch prevents the
 * consumer from committing offsets, so lag accumulates visibly in Grafana.
 * On OpenSearch recovery the consumer catches up and lag returns to zero.</p>
 */
@Component
@Getter
@Slf4j
@EnableScheduling
public class SearchMetrics {

    private static final String CONSUMER_GROUP = "search-service-group";
    private static final String TOPIC = "order.events.created";

    private final ConcurrentHashMap<Integer, AtomicLong> partitionLag = new ConcurrentHashMap<>();

    // Counters
    private final Counter indexingSuccessCounter;
    private final Counter indexingErrorCounter;

    // Timer
    private final Timer searchQueryTimerOrders;
    private final Timer searchQueryTimerProducts;

    private final AdminClient adminClient;
    private final MeterRegistry registry;

    public SearchMetrics(MeterRegistry registry,
                         @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        this.registry = registry;

        this.adminClient = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000",
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "8000"
        ));

        // Counters
        this.indexingSuccessCounter = Counter.builder("scalefulfill_search_indexing_total")
                .description("OpenSearch document indexing attempts by result")
                .tag("result", "indexed")
                .register(registry);

        this.indexingErrorCounter = Counter.builder("scalefulfill_search_indexing_total")
                .description("OpenSearch document indexing attempts by result")
                .tag("result", "error")
                .register(registry);

        // Histograms — SLO buckets in application.yml; Phase4 baseline: P50=19ms, P95=30ms
        this.searchQueryTimerOrders = Timer.builder("scalefulfill_search_query_duration")
                .description("OpenSearch query latency by query type")
                .tag("query_type", "order_search")
                .publishPercentileHistogram(true)
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(2))
                .register(registry);

        this.searchQueryTimerProducts = Timer.builder("scalefulfill_search_query_duration")
                .description("OpenSearch query latency by query type")
                .tag("query_type", "product_search")
                .publishPercentileHistogram(true)
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(2))
                .register(registry);

        // Seed lag gauges for partitions 0-3
        for (int p = 0; p < 4; p++) {
            final int partition = p;
            AtomicLong lagValue = new AtomicLong(0);
            partitionLag.put(partition, lagValue);
            Gauge.builder("scalefulfill_search_kafka_consumer_lag", lagValue, AtomicLong::get)
                    .description("Kafka consumer lag for search-service-group on order.events.created")
                    .tag("topic", TOPIC)
                    .tag("partition", String.valueOf(partition))
                    .register(registry);
        }
    }

    /**
     * Polls AdminClient every 15 s to update per-partition consumer lag.
     * This is the metric that rises during an OpenSearch outage (consumer
     * cannot process messages, offsets not committed) and falls to zero
     * upon recovery.
     */
    @Scheduled(fixedDelay = 15000, initialDelay = 12000)
    public void refreshKafkaConsumerLag() {
        try {
            ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(CONSUMER_GROUP);
            Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> committedOffsets =
                    offsetsResult.partitionsToOffsetAndMetadata().get(10, java.util.concurrent.TimeUnit.SECONDS);

            if (committedOffsets == null || committedOffsets.isEmpty()) {
                return;
            }

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

            for (Map.Entry<TopicPartition, org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo> entry : endOffsets.entrySet()) {
                TopicPartition tp = entry.getKey();
                long endOffset = entry.getValue().offset();
                long committedOffset = committedOffsets.getOrDefault(tp,
                        new org.apache.kafka.clients.consumer.OffsetAndMetadata(0)).offset();
                long lag = Math.max(0, endOffset - committedOffset);

                AtomicLong lagValue = partitionLag.computeIfAbsent(tp.partition(), p -> {
                    AtomicLong newLag = new AtomicLong(0);
                    Gauge.builder("scalefulfill_search_kafka_consumer_lag", newLag, AtomicLong::get)
                            .description("Kafka consumer lag for search-service-group on order.events.created")
                            .tag("topic", TOPIC)
                            .tag("partition", String.valueOf(tp.partition()))
                            .register(registry);
                    return newLag;
                });
                lagValue.set(lag);
            }

            log.debug("[search-metrics] Kafka consumer lag refreshed for group [{}]", CONSUMER_GROUP);
        } catch (Exception e) {
            log.warn("[search-metrics] Failed to refresh Kafka consumer lag: {}", e.getMessage());
        }
    }
}
