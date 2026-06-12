package com.aisa.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.integration.redis.util.RedisLockRegistry;

/**
 * Configures a distributed lock registry backed by Redis.
 *
 * <p>The orchestrator uses distributed locks to prevent duplicate invocation of the same
 * agent step when multiple orchestrator replicas consume from Kafka concurrently.
 * Lock TTL is set to 120 seconds, matching the per-agent timeout (Requirement 6.4).
 * If an instance crashes while holding a lock, Redis auto-expires it so another replica
 * can re-queue and retry the work within 30 seconds (Requirement 26.6).
 *
 * <p>Key namespace: {@code aisa:lock:orchestrator:}
 *
 * @see <a href="../../docs/scalability/redis-usage-strategy.md">Redis Usage Strategy</a>
 */
@Configuration
public class RedisLockConfiguration {

    /** Lock key prefix in Redis. */
    private static final String LOCK_REGISTRY_KEY = "aisa:lock:orchestrator";

    /** Lock expiry matching the per-agent invocation timeout (Req 6.4). */
    private static final long LOCK_EXPIRE_AFTER_MS = 120_000L;

    /**
     * Creates a {@link RedisLockRegistry} that the saga execution logic uses to acquire
     * per-step distributed locks before invoking an agent.
     *
     * @param connectionFactory the Redis connection factory auto-configured by Spring Boot
     * @return a lock registry with 120 s TTL per lock
     */
    @Bean
    public RedisLockRegistry redisLockRegistry(RedisConnectionFactory connectionFactory) {
        return new RedisLockRegistry(connectionFactory, LOCK_REGISTRY_KEY, LOCK_EXPIRE_AFTER_MS);
    }
}
