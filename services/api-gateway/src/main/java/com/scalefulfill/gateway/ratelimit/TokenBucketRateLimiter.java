package com.scalefulfill.gateway.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class TokenBucketRateLimiter {

    @Value("${scalefulfill.rate-limiter.capacity:20}")
    private long capacity;

    @Value("${scalefulfill.rate-limiter.refill-tokens-per-second:10}")
    private double refillRatePerSecond;

    private final ReactiveStringRedisTemplate redisTemplate;

    private volatile boolean redisAvailable = true;
    private volatile long lastRedisFailureTime = 0;
    private static final long REDIS_RETRY_INTERVAL_MS = 5000;

    // Resilient In-Memory Fallback if Redis is unreachable
    private final Map<String, TokenBucketState> inMemoryBuckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> tryAcquire(String clientKey) {
        long now = System.currentTimeMillis();
        String redisTokensKey = "ratelimit:tokens:" + clientKey;
        String redisTimestampKey = "ratelimit:ts:" + clientKey;

        // If Redis was recently marked unavailable, fail fast directly to in-memory bucket
        if (!redisAvailable && (now - lastRedisFailureTime) < REDIS_RETRY_INTERVAL_MS) {
            return Mono.just(tryAcquireInMemory(clientKey, now));
        }

        // Try Redis-backed token bucket first, guarded with Mono.defer
        return Mono.defer(() -> {
            if (redisTemplate == null) {
                return Mono.just(tryAcquireInMemory(clientKey, now));
            }
            return redisTemplate.opsForValue().get(redisTokensKey)
                .zipWith(redisTemplate.opsForValue().get(redisTimestampKey).defaultIfEmpty("0"))
                .flatMap(tuple -> {
                    redisAvailable = true;
                    double currentTokens = Double.parseDouble(tuple.getT1());
                    long lastRefill = Long.parseLong(tuple.getT2());

                    double elapsedSeconds = Math.max(0, (now - lastRefill) / 1000.0);
                    double refilledTokens = Math.min(capacity, currentTokens + (elapsedSeconds * refillRatePerSecond));

                    if (refilledTokens >= 1.0) {
                        double remainingTokens = refilledTokens - 1.0;
                        return redisTemplate.opsForValue().set(redisTokensKey, String.valueOf(remainingTokens), Duration.ofMinutes(5))
                                .and(redisTemplate.opsForValue().set(redisTimestampKey, String.valueOf(now), Duration.ofMinutes(5)))
                                .thenReturn(true);
                    } else {
                        return Mono.just(false);
                    }
                })
                .switchIfEmpty(
                        redisTemplate.opsForValue().set(redisTokensKey, String.valueOf(capacity - 1), Duration.ofMinutes(5))
                                .and(redisTemplate.opsForValue().set(redisTimestampKey, String.valueOf(now), Duration.ofMinutes(5)))
                                .doOnSuccess(v -> redisAvailable = true)
                                .thenReturn(true)
                );
        }).timeout(Duration.ofMillis(200))
        .onErrorResume(e -> {
            if (redisAvailable) {
                log.warn("[GATEWAY] Redis unavailable for rate limiting ({}), activating fast in-memory fallback", e.getMessage());
            }
            redisAvailable = false;
            lastRedisFailureTime = System.currentTimeMillis();
            return Mono.just(tryAcquireInMemory(clientKey, now));
        });
    }

    private synchronized boolean tryAcquireInMemory(String clientKey, long now) {
        TokenBucketState state = inMemoryBuckets.computeIfAbsent(clientKey,
                k -> new TokenBucketState(capacity, now));

        double elapsedSeconds = Math.max(0, (now - state.lastRefillTime) / 1000.0);
        state.tokens = Math.min(capacity, state.tokens + (elapsedSeconds * refillRatePerSecond));
        state.lastRefillTime = now;

        if (state.tokens >= 1.0) {
            state.tokens -= 1.0;
            return true;
        } else {
            return false;
        }
    }

    private static class TokenBucketState {
        double tokens;
        long lastRefillTime;

        TokenBucketState(double tokens, long lastRefillTime) {
            this.tokens = tokens;
            this.lastRefillTime = lastRefillTime;
        }
    }
}
