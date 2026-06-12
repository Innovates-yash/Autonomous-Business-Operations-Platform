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
| `aisa:ratelimit:gateway:`      | api-gateway           | Sliding-window rate-limit counters             | 60 s      |
| `aisa:cache:provider:`         | ai-provider-gateway   | Active provider selection + availability flags | 5 s       |
| `aisa:cache:project:`          | project-service       | Hot project reads, state lookups               | 60 s      |
| `aisa:chat:context:`           | ai-chat-service       | 20-message rolling context window per project  | 24 h      |
| `aisa:lock:orchestrator:`      | orchestrator-service  | Distributed locks for saga coordination        | 120 s     |

## Per-Service Redis Responsibilities

### auth-service — Sessions

- **What:** Spring Session with Redis store (`spring.session.store-type=redis`).
- **Why:** Any auth-service replica can validate or invalidate a session without sticky
  routing. On logout or role change, the session is deleted from Redis and every replica
  immediately respects the change (Req 2.13, 2.14).
- **Config:** `@EnableRedisHttpSession` with namespace `aisa:session:auth`.

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

### orchestrator-service — Distributed Locks

- **What:** Distributed locks (via Spring Integration Redis or Redisson) for saga
  step coordination.
- **Why:** When multiple orchestrator replicas consume from Kafka, a distributed lock
  prevents duplicate invocation of the same agent step. Lock TTL = 120 s matches the
  per-agent timeout (Req 6.4). If an instance crashes, the lock auto-expires and
  another replica can re-queue the work (Req 26.6).
- **Config:** `spring-boot-starter-data-redis` + `spring-integration-redis` (or Redisson).

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
