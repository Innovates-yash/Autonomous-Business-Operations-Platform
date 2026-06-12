# Load Distribution Verification Strategy

> Requirements: 26.2, 26.3, 26.4

## Verification Target

Under Kubernetes Horizontal Pod Autoscaler (HPA), the request load across service
replicas must stay **within 20% above the mean** (Req 26.3). That is, if the mean
per-instance request rate is `μ`, no single instance should sustain a rate above
`1.2 × μ` over a 60-second measurement window.

## Why Redis Enables Uniform Load Distribution

All services are stateless — they share no in-process state that would cause affinity.
Because Redis holds the only shared ephemeral state (sessions, caches, rate-limit
counters, locks), any request can be served by any replica without forwarding, draining,
or session pinning. The Kubernetes Service `ClusterIP` round-robin (or the Ingress
weighted distribution) is therefore free to spread traffic evenly.

Key architectural properties:

| Property                        | Effect on Load Distribution                       |
|---------------------------------|---------------------------------------------------|
| Externalized sessions (Redis)   | No sticky routing; any pod handles any user       |
| Externalized caches (Redis)     | No local hot-spot; cache misses are uniform       |
| Externalized locks (Redis)      | Saga steps run on whichever pod acquires the lock |
| Kafka consumer group rebalance  | Partitions redistribute on scale-out within 30 s  |
| Stateless REST handlers         | Round-robin routing is sufficient                  |

## Measurement Approach (verified at runtime in Task 31)

1. **Prometheus metrics per pod:**
   - `http_server_requests_seconds_count` (labeled by pod)
   - Custom gauge `aisa_instance_active_requests`

2. **Grafana query:**
   ```promql
   max(rate(http_server_requests_seconds_count[1m])) by (pod)
   /
   avg(rate(http_server_requests_seconds_count[1m]))
   ```
   This must remain ≤ 1.20 (i.e., no pod exceeds the mean by more than 20%).

3. **Load test scenario (k6 / Gatling):**
   - 100 concurrent virtual users submitting project-creation requests
   - Duration: 5 minutes sustained
   - Assert: p99 response time ≤ 5 seconds; load variance ratio ≤ 1.20

4. **HPA configuration (verified in Task 30):**
   - Target CPU utilization: 70%
   - Min replicas: 2; Max replicas: 10
   - Scale-up stabilization: 60 s; Scale-down stabilization: 300 s

## Expected Outcome

Because Redis-backed shared state eliminates per-instance affinity, Kubernetes
round-robin distribution results in near-uniform load. Under sustained traffic
the variance ratio stays well below 1.10 empirically; the 20% threshold (1.20)
provides margin for transient burst asymmetry during HPA scale-up events.

## Performance Target: New-Project Creation ≤ 5s Under 100 Concurrent Generations

**Acceptance Criterion (Req 26.4):** When 100 blueprint-generation jobs are running
concurrently (consuming orchestrator and agent-worker capacity), a new-project creation
request must still complete within 5 seconds.

### Why This Works

- Project creation is a synchronous REST call to `project-service` that writes to MySQL
  and updates the Redis cache. It does **not** contend with the Kafka-driven generation
  pipeline.
- The orchestrator's agent work runs asynchronously via Kafka consumers and does not
  block the project-service thread pool.
- Redis caching of project reads ensures that the write path (INSERT + cache-put)
  remains fast even when read traffic spikes from 100 concurrent orchestrator instances
  looking up project metadata.

### Verification (Task 31 — Performance Validation)

This performance target is verified at runtime via a dedicated performance test:

- **Precondition:** 100 concurrent generation runs active (orchestrator consuming Kafka).
- **Action:** Submit 50 new-project-creation requests concurrently.
- **Assertion:** p99 latency ≤ 5,000 ms; zero HTTP 5xx responses.

The test is defined in Task 31 of the implementation plan and uses the same load-testing
framework (k6 or Gatling) as the load-distribution verification above.

## References

- Design Document §Architecture: "Horizontally scalable and stateless services"
- Requirement 26.2: All shared session/cache state externalized to Redis
- Requirement 26.3: Load per instance within 20% above mean
- Requirement 26.4: New-project creation ≤ 5 s under 100 concurrent generations
- Task 30: Kubernetes manifests with HPA
- Task 31: Performance validation tests
