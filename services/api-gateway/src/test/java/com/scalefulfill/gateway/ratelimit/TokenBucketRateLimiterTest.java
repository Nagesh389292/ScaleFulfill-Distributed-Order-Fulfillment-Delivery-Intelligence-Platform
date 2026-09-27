package com.scalefulfill.gateway.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Token Bucket Rate Limiter Tests")
class TokenBucketRateLimiterTest {

    private TokenBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        // Mock redis to simulate offline/fallback mode for pure unit testing
        ReactiveStringRedisTemplate redisTemplate = Mockito.mock(ReactiveStringRedisTemplate.class);
        Mockito.when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis offline mock"));

        rateLimiter = new TokenBucketRateLimiter(redisTemplate);
        ReflectionTestUtils.setField(rateLimiter, "capacity", 5L);
        ReflectionTestUtils.setField(rateLimiter, "refillRatePerSecond", 2.0);
    }

    @Test
    @DisplayName("Allows requests up to bucket capacity (5 requests)")
    void testBurstWithinCapacityAllowed() {
        String client = "TEST_CLIENT_A";

        for (int i = 0; i < 5; i++) {
            Boolean allowed = rateLimiter.tryAcquire(client).block();
            assertTrue(allowed, "Request " + (i + 1) + " should be allowed within capacity");
        }

        // 6th request should exceed capacity and return false
        Boolean rejected = rateLimiter.tryAcquire(client).block();
        assertFalse(rejected, "Request exceeding capacity should be rejected with 429 condition");
    }

    @Test
    @DisplayName("Refills tokens after elapsed duration")
    void testRefillOverTime() throws InterruptedException {
        String client = "TEST_CLIENT_B";

        // Exhaust all 5 tokens
        for (int i = 0; i < 5; i++) {
            rateLimiter.tryAcquire(client).block();
        }
        assertFalse(rateLimiter.tryAcquire(client).block());

        // Wait 600ms (at 2 tokens/sec, ~1.2 tokens refill)
        Thread.sleep(600);

        Boolean allowedAfterRefill = rateLimiter.tryAcquire(client).block();
        assertTrue(allowedAfterRefill, "Token should have refilled after duration");
    }
}
