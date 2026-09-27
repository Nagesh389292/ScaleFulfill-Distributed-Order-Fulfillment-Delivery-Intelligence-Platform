# ADR-004: API Gateway Pattern and Redis-Backed Token Bucket Rate Limiting

## Status
Accepted

## Context
With microservices split into independent network endpoints (`order-service` on port 8081, `inventory-service` on port 8082), exposing internal service topologies directly to clients introduces critical security and stability vulnerabilities:
1. Clients must maintain hardcoded URLs for each backend service.
2. Cross-cutting concerns (authentication, correlation tracing, client rate limiting) would need to be duplicated across every individual microservice.
3. Sudden traffic spikes or malicious DDOS bursts can saturate backend thread pools and databases without an edge throttle.

## Decision
We implement a unified **API Gateway** deployed on the client-facing ingress port (`8080`) based on Spring Cloud Gateway:
1. **Dynamic Routing:** Routes `/api/v1/orders/**` to `order-service` and `/api/v1/inventory/**` to `inventory-service`.
2. **Correlation ID Propagation:** A global filter checks for or generates an `X-Correlation-Id` header, injecting it into downstream service requests and appending it to client HTTP responses for end-to-end distributed traceability.
3. **Redis-Backed Token Bucket Rate Limiting:**
   - Enforces a Token Bucket algorithm per client identifier (customer ID or client IP address).
   - Configured with a burst capacity of 20 tokens and a refill rate of 10 tokens/second (or 100/minute).
   - If token balance is exhausted, the gateway immediately returns `429 Too Many Requests` with `Retry-After: 1` and a structured JSON error body without forwarding load to downstream services.
   - Includes a resilient in-memory fallback if Redis connectivity is temporarily interrupted.

## Consequences
### Positive
- Single unified entry point for clients, masking internal network topologies and ports.
- Upstream protection preventing downstream database connection starvation during traffic spikes.
- Consistent observability and structured 429 rate limit responses.

### Negative / Trade-offs
- Introduces an extra network hop and fractional gateway latency (~2-5ms).
- The gateway represents a single point of ingress failure, requiring multi-instance deployment and health-checking in production.
