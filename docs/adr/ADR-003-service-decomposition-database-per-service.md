# ADR-003: Service Decomposition and Database-per-Service Architecture

## Status
Accepted

## Context
In Phase 1, ScaleFulfill was built as a modular monolith sharing a single PostgreSQL database instance and ACID transaction boundary. Concurrency benchmarks revealed that while transactional consistency was preserved, optimistic locking on hot inventory rows became the throughput ceiling, and changes to order schemas risked locking shared database resources.

To scale engineering velocity and isolate database failure domains, we must decompose the domain into autonomous bounded contexts:
1. **Order Service:** Manages customer order lifecycles, totals, and state machine progressions.
2. **Inventory Service:** Manages multi-center stock levels, reservations, releases, and warehouse metadata.

## Decision
We decouple the monolithic application into two standalone microservices, each with its own independent Maven build, deployment artifact, and dedicated PostgreSQL database:
- `order-service` -> `order_db`
- `inventory-service` -> `inventory_db`

### Invariants & Rules:
1. **Zero Cross-Database Joins:** Services cannot directly query each other's databases. `order-service` possesses no database credentials or network access to `inventory_db`, and vice versa.
2. **Synchronous Contract Boundaries:** Inter-service interactions take place over explicit REST APIs using typed DTOs.
3. **Dedicated Evolution:** Flyway database migrations run independently in each service container during deployment.

## Consequences
### Positive
- Independent scaling: Inventory lookups and reservations can scale replicas independently of order placement.
- Database isolation: Slow queries or index re-builds in `inventory_db` do not exhaust connection pools in `order_db`.
- Aligns domain ownership with microservice bounded contexts.

### Negative / Trade-offs
- Eliminates local ACID transactions across order creation and stock reservation: if `inventory-service` fails mid-flight, `order-service` cannot rely on database rollback and must manage state compensation (paving the way for Phase 3 Kafka outbox/Saga).
- Increased operational complexity managing multiple service runtimes, database instances, and network hops.
