package com.scalefulfill.gateway.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

@DisplayName("Multi-Instance Distributed Redis Rate Limiting Tests")
class DistributedRedisRateLimiterTest {

    private TokenBucketRateLimiter gatewayInstanceA;
    private TokenBucketRateLimiter gatewayInstanceB;

    // Simulated Shared Redis Key-Value Store
    private final Map<String, String> sharedRedisStore = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        sharedRedisStore.clear();

        // Create reactive mock that simulates Redis cluster storage
        ReactiveStringRedisTemplate sharedRedisTemplate = Mockito.mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> valueOps = Mockito.mock(ReactiveValueOperations.class);

        Mockito.when(sharedRedisTemplate.opsForValue()).thenReturn(valueOps);

        // Redis GET behavior: read from shared store
        Mockito.when(valueOps.get(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String val = sharedRedisStore.get(key);
            return val != null ? Mono.just(val) : Mono.empty();
        });

        // Redis SET behavior: write to shared store
        Mockito.when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            String val = invocation.getArgument(1);
            sharedRedisStore.put(key, val);
            return Mono.just(true);
        });

        // Instantiate Gateway Instance A
        gatewayInstanceA = new TokenBucketRateLimiter(sharedRedisTemplate);
        ReflectionTestUtils.setField(gatewayInstanceA, "capacity", 5L);
        ReflectionTestUtils.setField(gatewayInstanceA, "refillRatePerSecond", 0.01);

        // Instantiate Gateway Instance B (distinct JVM/object instance with separate memory)
        gatewayInstanceB = new TokenBucketRateLimiter(sharedRedisTemplate);
        ReflectionTestUtils.setField(gatewayInstanceB, "capacity", 5L);
        ReflectionTestUtils.setField(gatewayInstanceB, "refillRatePerSecond", 0.01);
    }

    @Test
    @DisplayName("Gateway A and Gateway B enforce shared token bucket capacity across distributed instances")
    void testDistributedRateLimitingAcrossInstances() {
        String customerId = "ENTERPRISE_CUSTOMER_101";

        // 1. Gateway Instance A handles 3 requests
        assertTrue(gatewayInstanceA.tryAcquire(customerId).block(), "Gateway A: Request 1 allowed");
        assertTrue(gatewayInstanceA.tryAcquire(customerId).block(), "Gateway A: Request 2 allowed");
        assertTrue(gatewayInstanceA.tryAcquire(customerId).block(), "Gateway A: Request 3 allowed");

        // 2. Gateway Instance B receives request 4 and 5 (only 2 tokens remaining in Redis)
        assertTrue(gatewayInstanceB.tryAcquire(customerId).block(), "Gateway B: Request 4 allowed from shared Redis pool");
        assertTrue(gatewayInstanceB.tryAcquire(customerId).block(), "Gateway B: Request 5 allowed from shared Redis pool");

        // 3. Both instances should now observe an exhausted bucket in Redis
        Boolean aRejected = gatewayInstanceA.tryAcquire(customerId).block();
        assertFalse(aRejected, "Gateway A: Request 6 rejected (distributed bucket exhausted)");

        Boolean bRejected = gatewayInstanceB.tryAcquire(customerId).block();
        assertFalse(bRejected, "Gateway B: Request 7 rejected (distributed bucket exhausted)");

        // Verify shared Redis keys exist and reflect the exhausted state
        assertTrue(sharedRedisStore.containsKey("ratelimit:tokens:" + customerId));
        assertTrue(sharedRedisStore.containsKey("ratelimit:ts:" + customerId));
        double remainingTokensInRedis = Double.parseDouble(sharedRedisStore.get("ratelimit:tokens:" + customerId));
        assertTrue(remainingTokensInRedis < 1.0, "Remaining tokens in Redis should be less than 1");
    }
}
