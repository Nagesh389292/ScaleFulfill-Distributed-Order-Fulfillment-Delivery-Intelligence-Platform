package com.scalefulfill.gateway.filter;

import com.scalefulfill.gateway.ratelimit.TokenBucketRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimiterGlobalFilter implements GlobalFilter, Ordered {

    private final TokenBucketRateLimiter rateLimiter;

    @Value("${scalefulfill.rate-limiter.enabled:true}")
    private boolean enabled;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }

        // Identify client: use X-Customer-Id if present, else remote address IP
        String clientKey = exchange.getRequest().getHeaders().getFirst("X-Customer-Id");
        if (clientKey == null || clientKey.isBlank()) {
            if (exchange.getRequest().getRemoteAddress() != null) {
                clientKey = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
            } else {
                clientKey = "anonymous";
            }
        }

        final String resolvedKey = clientKey;

        return rateLimiter.tryAcquire(resolvedKey).flatMap(allowed -> {
            if (allowed) {
                return chain.filter(exchange);
            } else {
                log.warn("[GATEWAY 429] Rate limit exceeded for client [{}]", resolvedKey);
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                response.getHeaders().add(HttpHeaders.RETRY_AFTER, "1");

                String errorPayload = String.format("""
                        {
                          "timestamp": "%s",
                          "status": 429,
                          "error": "TOO_MANY_REQUESTS",
                          "message": "Token bucket capacity exceeded for client [%s]. Rate limit enforced.",
                          "path": "%s"
                        }
                        """, Instant.now(), resolvedKey, exchange.getRequest().getURI().getPath());

                DataBuffer buffer = response.bufferFactory().wrap(errorPayload.getBytes(StandardCharsets.UTF_8));
                return response.writeWith(Mono.just(buffer));
            }
        });
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1; // Immediately following correlation ID filter
    }
}
