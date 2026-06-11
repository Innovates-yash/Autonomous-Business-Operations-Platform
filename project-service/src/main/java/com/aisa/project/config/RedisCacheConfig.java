package com.aisa.project.config;

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
 * Redis-backed caching configuration for project-service (Requirements 26.2, 26.3).
 *
 * <p>Caching project reads in Redis enables stateless horizontal scaling — any
 * project-service instance serves any request from the shared cache rather than
 * always hitting MySQL. Under load (100 concurrent blueprint generations), cached
 * project lookups keep the new-project-creation latency within the 5-second target
 * (Requirement 26.4).
 *
 * <p>Key namespace: {@code aisa:cache:project:} ensures no collision with session,
 * rate-limit, or notification keys in the shared Redis instance.
 *
 * <p>Cache TTLs:
 * <ul>
 *   <li>{@code projects} — 5 minutes (project metadata changes infrequently)</li>
 *   <li>{@code projectStates} — 30 seconds (state transitions are latency-sensitive)</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("aisa:cache:project:")
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        RedisCacheConfiguration statesCacheConfig = defaultConfig
                .entryTtl(Duration.ofSeconds(30));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("projectStates", statesCacheConfig)
                .build();
    }
}
