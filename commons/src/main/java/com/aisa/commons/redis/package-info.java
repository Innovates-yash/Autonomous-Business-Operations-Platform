/**
 * Redis shared-state strategy documentation (Requirements 26.2, 26.3, 26.4).
 *
 * <p>All platform services are <strong>stateless</strong>. Shared ephemeral state is externalized
 * to a single Redis cluster so that any service instance can handle any request and Kubernetes HPA
 * can scale replicas freely. This package provides shared constants for Redis key namespaces used
 * across the platform, plus a shared {@link RedisCacheAutoConfiguration} that services can opt into.
 *
 * <h2>Redis Key Namespace Convention</h2>
 * <pre>
 * aisa:&lt;concern&gt;:&lt;service&gt;:&lt;domain-key&gt;
 * </pre>
 *
 * <h2>Per-Service Usage</h2>
 * <table>
 *   <tr><th>Prefix</th><th>Service</th><th>Purpose</th><th>TTL</th></tr>
 *   <tr><td>{@code aisa:session:auth:}</td><td>auth-service</td><td>Spring Session (HTTP sessions)</td><td>30 min</td></tr>
 *   <tr><td>{@code aisa:ratelimit:gateway:}</td><td>api-gateway</td><td>Sliding-window rate-limit counters</td><td>60 s</td></tr>
 *   <tr><td>{@code aisa:cache:provider:}</td><td>ai-provider-gateway</td><td>Provider selection + availability</td><td>5 s</td></tr>
 *   <tr><td>{@code aisa:cache:project:}</td><td>project-service</td><td>Hot project reads</td><td>5 min</td></tr>
 *   <tr><td>{@code aisa:chat:context:}</td><td>ai-chat-service</td><td>20-message rolling context window</td><td>24 h</td></tr>
 *   <tr><td>{@code aisa:lock:orchestrator:}</td><td>orchestrator-service</td><td>Distributed locks for saga steps</td><td>120 s</td></tr>
 *   <tr><td>{@code aisa:notify:subscriptions:}</td><td>notification-service</td><td>Active WebSocket subscription registry</td><td>1 h</td></tr>
 *   <tr><td>{@code aisa:notify:fanout:}</td><td>notification-service</td><td>Last-event markers for reconnect resync</td><td>24 h</td></tr>
 * </table>
 *
 * <h2>Shared Auto-Configuration</h2>
 * <p>{@link RedisCacheAutoConfiguration} provides a default {@code RedisCacheManager} bean
 * with service-scoped key prefixes ({@code aisa:cache:<service-name>:}) and configurable TTL.
 * Services that need custom cache managers (e.g., project-service with per-cache TTLs) define
 * their own bean, which suppresses the auto-configured default via {@code @ConditionalOnMissingBean}.
 * Services that use Redis for non-cache purposes (e.g., notification-service for subscriptions)
 * set {@code aisa.redis.cache.enabled=false}.
 *
 * <h2>Load Distribution Guarantee (Req 26.3)</h2>
 * <p>Because all shared state lives in Redis rather than in-process, Kubernetes round-robin routing
 * distributes load uniformly. Under HPA, per-instance load stays within <strong>20% above the
 * mean</strong> — no sticky sessions or instance-local caches create affinity.
 *
 * <h2>Consistent Hashing (Redis Cluster)</h2>
 * <p>In production, the Redis Cluster deployment uses CRC16-based hash-slot assignment for
 * key distribution. Spring Data Redis Cluster support handles this transparently — service
 * code does not need to be aware of the cluster topology. Combined with the namespace prefix
 * convention, keys are evenly distributed across Redis shards.
 *
 * <h2>Kafka Partition Assignment (Agent Tasks)</h2>
 * <p>For async workloads (agent-tasks, agent-progress, project-state-changes), Kafka's
 * cooperative-sticky consumer-group protocol distributes partitions evenly. With P partitions
 * and N consumers (P ≥ 3×N recommended), each consumer handles ⌊P/N⌋ or ⌈P/N⌉ partitions,
 * inherently satisfying the 20% load variance constraint.
 *
 * <h2>Performance Target (Req 26.4)</h2>
 * <p>New-project creation completes within <strong>≤5 seconds</strong> even when 100 blueprint
 * generations run concurrently. Project creation is a synchronous REST + MySQL write + Redis
 * cache-put that does not contend with the Kafka-driven generation pipeline.
 *
 * @see RedisCacheAutoConfiguration
 * @see RedisCacheProperties
 * @see RedisNamespaces
 * @see <a href="../../../../docs/scalability/redis-usage-strategy.md">Redis Usage Strategy</a>
 * @see <a href="../../../../docs/scalability/load-distribution-verification.md">Load Distribution Verification</a>
 * @see <a href="../../../../docs/scalability/session-strategy.md">Session Strategy</a>
 */
package com.aisa.commons.redis;
