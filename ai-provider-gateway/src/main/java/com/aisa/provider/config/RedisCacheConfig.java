package com.aisa.provider.config;

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
 * Redis-backed caching configuration for ai-provider-gateway (Requirements 26.2, 26.3).
 *
 * <p>Provider selection and health state are cached in Redis so all gateway instances
 * share a consistent view of which provider is active and which are marked unavailable.
 * This supports stateless horizontal scaling — any instance can route an AI request
 * without local-only state divergence.
 *
 * <p>Key namespace: {@code aisa:cache:provider:} ensures no collision with session,
 * rate-limit, or other service keys in the shared Redis instance.
 *
 * <p>Cache TTLs:
 * <ul>
 *   <li>{@code providerSelection} — 5 seconds (must converge quickly after admin change)</li>
 *   <li>{@code providerHealth} — 10 seconds (health re-checks are frequent)</li>
 *   <li>{@code providerConfig} — 2 minutes (provider configs change rarely)</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("aisa:cache:provider:")
                .entryTtl(Duration.ofMinutes(2))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        RedisCacheConfiguration selectionCacheConfig = defaultConfig
                .entryTtl(Duration.ofSeconds(5));

        RedisCacheConfiguration healthCacheConfig = defaultConfig
                .entryTtl(Duration.ofSeconds(10));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("providerSelection", selectionCacheConfig)
                .withCacheConfiguration("providerHealth", healthCacheConfig)
                .build();
    }
}
