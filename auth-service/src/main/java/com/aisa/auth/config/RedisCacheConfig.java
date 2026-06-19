package com.aisa.auth.config;

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
 * Redis-backed caching configuration for auth-service (Requirements 26.2, 26.3).
 *
 * <p>Role and permission lookups are the most frequent authorization operations.
 * Caching them in Redis ensures:
 * <ul>
 *   <li>Any auth-service instance serves any authorization decision from the shared cache,
 *       enabling stateless horizontal scaling (Req 26.2).</li>
 *   <li>Authorization decisions return within 500 ms (Req 2.4) even under high load.</li>
 *   <li>Database read contention is minimized during concurrent blueprint generations
 *       (Req 26.4).</li>
 * </ul>
 *
 * <p>Key namespace: {@code aisa:cache:auth:} ensures no collision with session, rate-limit,
 * or other service keys in the shared Redis instance.
 *
 * <p>Cache TTLs:
 * <ul>
 *   <li>{@code rolePermissions} — 5 seconds (must converge within 5s after role change, Req 2.13)</li>
 *   <li>{@code userRoles} — 5 seconds (role assignment changes take effect within 5s, Req 2.13)</li>
 * </ul>
 *
 * <p>Cache eviction is triggered by {@link com.aisa.auth.service.RoleAssignmentService}
 * on role change to satisfy Requirement 2.14 (invalidate permissions cached under the
 * previous role).
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("aisa:cache:auth:")
                .entryTtl(Duration.ofSeconds(5))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        RedisCacheConfiguration rolePermissionsConfig = defaultConfig
                .entryTtl(Duration.ofSeconds(5));

        RedisCacheConfiguration userRolesConfig = defaultConfig
                .entryTtl(Duration.ofSeconds(5));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("rolePermissions", rolePermissionsConfig)
                .withCacheConfiguration("userRoles", userRolesConfig)
                .build();
    }
}
