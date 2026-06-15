# Redis Usage Strategy

> Requirements: 26.2, 26.3, 26.4 | Design: Horizontally scalable and stateless services

## Overview

All platform services are designed to be **stateless**. Any instance of any service can
handle any request because all shared ephemeral state lives in a single Redis cluster.
This document defines how Redis is partitioned across concerns, what keys each service
owns, and the namespace conventions that prevent collision.

## Namespace Convention

All Redis keys follow the pattern:

```
aisa:<concern>:<service>:<domain-key>
```

| Prefix                         | Owner Service         | Purpose                                        | TTL       |
|--------------------------------|-----------------------|------------------------------------------------|-----------|
| `aisa:session:auth:`           | auth-service          | HTTP sessions (Spring Session)                 | 30 min    |
| `aisa:cache:auth:`             | auth-service          | Role/permission authorization decision cache   | 30 s      |
| `aisa:ratelimit:gateway:`      | api-gateway           | Sliding-window rate-limit counters             | 60 s      |
| `aisa:cache:provider:`         | ai-provider-gateway   | Active provider selection + availability flags | 5 s       |
| `aisa:cache:project:`          | project-service       | Hot project reads, state lookups               | 60 s      |
| `aisa:cache:orchestrator:`     | orchestrator-service  | Agent status + generation run state cache      | 10–15 s   |
| `aisa:chat:context:`           | ai-chat-service       | 20-message rolling context window per project  | 24 h      |
| `aisa:lock:orchestrator:`      | orchestrator-service  | Distributed locks for saga coordination        | 120 s     |

## Per-Service Redis Responsibilities

### auth-service — Sessions and Role/Permission Cache

- **What:** Spring Session with Redis store (`spring.session.store-type=redis`); Spring Cache
  abstraction (`@EnableCaching` + `@Cacheable`) for authorization decisions.
- **Why:** Any auth-service replica can validate or invalidate a session without sticky
  routing. On logout or role change, the session is deleted from Redis and every replica
  immediately respects the change (Req 2.13, 2.14). Role/permission lookups are the most
  frequent authorization operation; caching them in Redis keeps decisions within 500 ms
  (Req 2.4) even under heavy concurrent load and supports stateless horizontal scaling.
- **Cache details:**
  - `rolePermissions` cache: keyed by `userId:permission`, TTL 30 s.
  - Evicted on role assignment via `@CacheEvict(allEntries = true)` in `RoleAssignmentService`.
- **Config:** `@EnableRedisHttpSession` with namespace `aisa:session:auth`;
  `@EnableCaching` with `RedisCacheManager` prefixing `aisa:cache:auth:`.

### api-gateway — Rate-Limit Counters

- **What:** Sliding-window counters keyed by `principal + window-start`.
- **Why:** Edge rate limiting at 100 req/min per authenticated client (Req 25.4). Redis
  atomic increments ensure accurate counting even when multiple gateway replicas serve the
  same client concurrently.
- **Config:** `spring-boot-starter-data-redis-reactive` (reactive gateway stack).

### ai-provider-gateway — Provider Selection Cache

- **What:** Cached active provider selection and per-provider availability flags.
- **Why:** Every ai-provider-gateway instance must route to the same active provider
  within 5 seconds of a selection change (Req 20.2). Redis provides the shared view;
  each instance polls or subscribes for changes.
- **Config:** `spring-boot-starter-data-redis` with `spring.cache.type=redis`.

### ai-chat-service — Context Window

- **What:** The 20 most recent messages per conversation stored as a Redis list.
- **Why:** Assembling the context window for AI requests must be fast (<50 ms).
  Full chat history persists in MySQL; the rolling window is a Redis read-through cache
  rebuilt from MySQL on miss (Req 5.3).
- **Config:** `spring-boot-starter-data-redis` with custom `RedisTemplate<String, ChatMessage>`.

### project-service — Read Cache

- **What:** Recently accessed project metadata and state.
- **Why:** Under 100 concurrent blueprint generations, the orchestrator and agents
  repeatedly read project metadata. Caching in Redis avoids MySQL hot-spot contention
  and keeps response time ≤5 s (Req 26.4).
- **Config:** `spring-boot-starter-data-redis` with `spring.cache.type=redis`.

### orchestrator-service — Distributed Locks and Agent Status Cache

- **What:** Distributed locks (via Spring Integration Redis) for saga step coordination;
  Spring Cache abstraction (`@EnableCaching`) for agent status and generation run reads.
- **Why:** When multiple orchestrator replicas consume from Kafka, a distributed lock
  prevents duplicate invocation of the same agent step. Lock TTL = 120 s matches the
  per-agent timeout (Req 6.4). If an instance crashes, the lock auto-expires and
  another replica can re-queue the work (Req 26.6). Agent status caching reduces DB
  round-trips when the saga engine evaluates readiness of dependent agents across
  concurrent generation runs.
- **Cache details:**
  - `agentStatus` cache: TTL 10 s (invocation status changes frequently during generation).
  - `generationRuns` cache: TTL 15 s (run-level state is less volatile).
- **Config:** `spring-boot-starter-data-redis` + `spring-integration-redis`;
  `@EnableCaching` with `RedisCacheManager` prefixing `aisa:cache:orchestrator:`.

## Deployment Topology

```
┌────────────────────────────────────────────────────────────────────┐
│  Kubernetes Cluster (multi-AZ)                                      │
│                                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────────┐    │
│  │ Gateway  │  │ Auth ×N  │  │ Project  │  │ Orchestrator ×N  │    │
│  │  ×N      │  │          │  │  ×N      │  │                  │    │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────────┬─────────┘    │
│       │              │             │                  │              │
│       └──────────────┴─────────────┴──────────────────┘              │
│                              │                                       │
│                    ┌─────────▼──────────┐                            │
│                    │   Redis (Cluster)   │                            │
│                    │  ElastiCache / K8s  │                            │
│                    │  replica set        │                            │
│                    └────────────────────┘                            │
└────────────────────────────────────────────────────────────────────┘
```

All service replicas connect to the **same Redis endpoint**. Under Kubernetes HPA,
new pods connect to Redis on startup and immediately participate in load handling
without any migration or warm-up step.

## Load Distribution Strategy (Requirement 26.3)

The ≤20% above-mean load distribution target is achieved through the combination of:

1. **Stateless services + shared Redis state:** Because all session, cache, and context
   data lives in Redis, the load balancer (Kubernetes Service or Ingress) can use
   round-robin or least-connections routing without sticky sessions. Any instance
   serves any request equally well.

2. **Kafka partition assignment:** For async workloads (agent tasks, progress events),
   Kafka's consumer-group protocol distributes partitions evenly across consumer
   instances. With N consumers and P partitions (P ≥ N), each consumer gets ⌊P/N⌋ or
   ⌈P/N⌉ partitions — inherently within 20% of the mean for P ≥ 3×N.

3. **Redis-backed rate limiting at the gateway:** Rate-limit counters in Redis are
   shared across all gateway replicas. This ensures accurate per-client enforcement
   regardless of which replica handles the request, preventing any single replica from
   bearing disproportionate load from a burst client.

4. **No warm-up or affinity requirements:** New pod replicas added by HPA connect to
   Redis on startup and immediately serve requests at full capacity. There is no
   migration, state transfer, or cache-warm period that would cause uneven distribution.

### Verification approach (task 31)

Deploy N ≥ 3 replicas → route 1000 requests → scrape per-pod
`http_server_requests_seconds_count` from Prometheus → assert
`max(count) ≤ 1.20 × mean(count)`.

## Project Creation ≤5s Under 100 Concurrent Generations (Requirement 26.4)

The ≤5-second new-project-creation target is met by architectural separation:

- **Generation workload is async (Kafka):** Blueprint generation is enqueued to Kafka
  topics and processed by dedicated consumer pods. It does not block the project-service
  thread pool or MySQL connection pool.

- **Project creation is a lightweight INSERT:** The project-service REST path performs
  a single MySQL INSERT (project + idea). This path shares no resource contention with
  the Kafka-driven agent orchestration.

- **Redis caching reduces read contention:** During heavy generation, repeated project
  reads (by orchestrator, agents) are served from Redis cache, not MySQL — leaving the
  MySQL write path uncongested for new-project INSERTs.

Combined, these ensure that even with 100 concurrent generations saturating Kafka and
the AI provider, the synchronous project-creation REST call completes well within 5s.

## Failure Handling

| Scenario                        | Behavior                                            |
|---------------------------------|-----------------------------------------------------|
| Redis unavailable (short)       | Circuit breaker opens; services degrade gracefully  |
| Redis unavailable (extended)    | Sessions fail to validate; rate limits not enforced |
| Redis data loss (failover)      | Sessions require re-login; caches rebuild from MySQL|
| Key expiry race                 | TTLs are conservative; double-checked on write      |

## References

- Design Document §Architecture: "Shared session and cache state lives in Redis"
- Requirement 26.2: Shared state externalized for stateless scaling
- Requirement 26.3: Load distribution within 20% above mean across instances
- Requirement 26.4: New-project creation ≤5 s under 100 concurrent generations
