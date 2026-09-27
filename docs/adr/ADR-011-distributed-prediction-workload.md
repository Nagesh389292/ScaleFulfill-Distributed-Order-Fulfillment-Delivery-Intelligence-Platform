# ADR-011: Distributed Prediction Workload & Analytical Estimator

## Status
Accepted (Phase 6)

## Context
In large-scale fulfillment networks such as Amazon, incoming customer orders require near-real-time prediction of delivery fulfillment estimated time of arrival (ETA) and product demand velocity scores to guide routing, carrier selection, and localized inventory cross-docking.

Performing these computations synchronously within the transactional Order Service path introduces dangerous thread blocking, increased write latency, and cascading failure risk. Furthermore, simply deploying unconstrained thread pools creates severe memory leaks and unbounded queue growth during flash-sale burst traffic.

## Decision
1. **Asynchronous Distributed Workload:** 
   The prediction workload is completely decoupled from the transactional path by consuming `order.events.created` events asynchronously from Apache Kafka via a dedicated `prediction-service` consumer group (`prediction-service-group`).
2. **Deterministic Analytical Estimator Baseline (`eta-v1`):**
   Rather than treating prediction as an opaque "black-box ML" model prematurely, we implement a transparent, deterministic kinematic estimator:
   $$T_{\text{ETA}} = \left(\frac{\text{Distance}}{V_{\text{transit}}}\right) \times 60 + T_{\text{handling}} + \left(\frac{Q_{\text{depth}}}{\text{DispatchRate}}\right) \times 60$$
   where physical distance is computed via the Haversine great-circle formula, $V_{\text{transit}}$ reflects dynamic carrier transit velocity, and queue depth tracks FC backpressure. The prediction result explicitly records `modelVersion="eta-v1"`, establishing a clean experimental baseline for future learned models (`eta-v2`).
3. **Bounded Worker Pool with Active Backpressure:**
   Work is dispatched to a custom `ThreadPoolExecutor` with strictly bounded capacity (`coreSize=4`, `maxSize=8`, `queueCapacity=1000`). If queue capacity is exceeded, an explicit `RejectedExecutionHandler` records `scalefulfill_prediction_total{status="rejected"}` and applies backpressure rather than allowing unbounded memory growth.
4. **Stale Prediction Filtering SLA ($\tau_{\text{stale}}$):**
   Predictions carry a time-to-live (`expiresAt = generatedAt + 15m`). If accessed after expiration, the prediction is marked stale (`stale=true`), recorded in `scalefulfill_prediction_stale_total`, and filtered from downstream dispatch decisions.
5. **Zero Upstream Coupling via Local Read Models:**
   The service maintains a local read model of Fulfillment Center geolocations, queue depths, and customer indices, eliminating synchronous HTTP callbacks to Order Service or Inventory Service.

## Consequences
- **Positive:** Transactional write path remains instantaneous (<15ms) while predictions process concurrently at hundreds of events/sec.
- **Positive:** Bounded queues prevent OutOfMemoryErrors under sustained traffic spikes.
- **Positive:** Explicit model versioning (`eta-v1`) enables A/B testing and comparative model evaluation.
- **Trade-off:** Predictions are eventually consistent; orders queried in the first ~25–50ms post-creation may experience brief asynchronous lag before prediction persistence completes.
