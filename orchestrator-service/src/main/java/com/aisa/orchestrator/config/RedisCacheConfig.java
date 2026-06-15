package com.aisa.orchestrator.config;

import java.time.Duration;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis-backed caching configuration for orchestrator-service (Requirements 26.2, 26.3).
 *
 * <p>The orchestrator frequently reads agent invocation status and generation run state
 * when determining which agents are ready to dispatch. Caching these reads in Redis:
 * <ul>
 *   <li>Reduces database round-trips during concurrent generation runs</li>
 *   <li>Supports stateless horizontal scaling — any orchestrator replica can resume
 *       or coordinate a saga from the shared cache (Req 26.2)</li>
 *   <li>Keeps the project-creation path uncongested by reducing MySQL read load
 *       from the orchestration layer (Req 26.4)</li>
 * </ul>
 *
 * <p>Key namespace: {@code aisa:cache:orchestrator:} ensures no collision with lock,
 * session, or other service keys in the shared Redis instance.
 *
 * <p>Cache TTLs:
 * <ul>
 *   <li>{@code agentStatus} — 10 seconds (status changes frequently during generation)</li>
 *   <li>{@code generationRuns} — 15 seconds (run state changes are propagated quickly)</li>
 * </ul>
 *
 * <p>Note: Write-through invalidation occurs naturally as saga operations always write
 * to MySQL first, and the short TTLs ensure stale reads are bounded.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("aisa:cache:orchestrator:")
                .entryTtl(Duration.ofSeconds(15))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        RedisCacheConfiguration agentStatusConfig = defaultConfig
                .entryTtl(Duration.ofSeconds(10));

        RedisCacheConfiguration generationRunsConfig = defaultConfig
                .entryTtl(Duration.ofSeconds(15));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("agentStatus", agentStatusConfig)
                .withCacheConfiguration("generationRuns", generationRunsConfig)
                .build();
    }
}
