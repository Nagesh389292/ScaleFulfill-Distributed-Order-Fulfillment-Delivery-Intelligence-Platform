package com.scalefulfill.prediction.repository;

import com.scalefulfill.prediction.model.DeliveryPrediction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe in-memory store with TTL and stale prediction evaluation.
 */
@Slf4j
@Repository
public class PredictionRepository {

    private final Map<String, DeliveryPrediction> store = new ConcurrentHashMap<>();
    private final AtomicLong staleAccessCount = new AtomicLong(0);

    public DeliveryPrediction save(DeliveryPrediction prediction) {
        store.put(prediction.getOrderId(), prediction);
        return prediction;
    }

    public Optional<DeliveryPrediction> findByOrderId(String orderId) {
        DeliveryPrediction pred = store.get(orderId);
        if (pred == null) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        if (pred.isExpired(now) && !pred.isStale()) {
            pred.setStale(true);
            staleAccessCount.incrementAndGet();
            log.debug("[prediction-store] Prediction for order {} expired at {} (now: {})", orderId, pred.getExpiresAt(), now);
        }
        return Optional.of(pred);
    }

    public Optional<DeliveryPrediction> findValidByOrderId(String orderId) {
        return findByOrderId(orderId).filter(p -> !p.isStale());
    }

    public Collection<DeliveryPrediction> findAll() {
        return store.values();
    }

    public long size() {
        return store.size();
    }

    public long getStaleAccessCount() {
        return staleAccessCount.get();
    }

    public void clear() {
        store.clear();
        staleAccessCount.set(0);
    }
}
