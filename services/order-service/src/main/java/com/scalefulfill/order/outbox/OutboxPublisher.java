package com.scalefulfill.order.outbox;

import com.scalefulfill.order.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "scalefulfill.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final int MAX_RETRIES = 5;

    @Scheduled(fixedDelayString = "${scalefulfill.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, 50));

        if (pendingEvents.isEmpty()) {
            return;
        }

        for (OutboxEvent event : pendingEvents) {
            try {
                String topic = resolveTopicForEvent(event.getEventType());
                // Use aggregateId as partition key for chronological order consistency
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                event.setStatus(OutboxStatus.PUBLISHED);
                                event.setProcessedAt(Instant.now());
                                outboxEventRepository.save(event);
                                log.info("[OUTBOX] Published event [{}] (aggregate: {}) to topic [{}] partition [{}]",
                                        event.getId(), event.getAggregateId(), topic,
                                        result.getRecordMetadata().partition());
                            } else {
                                handlePublishFailure(event, ex);
                            }
                        });
            } catch (Exception ex) {
                handlePublishFailure(event, ex);
            }
        }
    }

    private void handlePublishFailure(OutboxEvent event, Throwable ex) {
        log.error("[OUTBOX] Failed publishing event [{}] to Kafka: {}", event.getId(), ex.getMessage());
        event.setRetryCount(event.getRetryCount() + 1);
        if (event.getRetryCount() >= MAX_RETRIES) {
            event.setStatus(OutboxStatus.FAILED);
            log.error("[OUTBOX] Event [{}] exceeded max retry threshold ({}). Marked FAILED.",
                    event.getId(), MAX_RETRIES);
        }
        event.setProcessedAt(Instant.now());
        outboxEventRepository.save(event);
    }

    private String resolveTopicForEvent(String eventType) {
        if ("OrderCreatedEvent".equalsIgnoreCase(eventType) || "OrderCreated".equalsIgnoreCase(eventType)) {
            return KafkaTopicConfig.TOPIC_ORDER_CREATED;
        }
        return KafkaTopicConfig.TOPIC_ORDER_CREATED;
    }
}
