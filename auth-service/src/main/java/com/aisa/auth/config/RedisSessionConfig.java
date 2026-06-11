package com.aisa.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Redis-backed HTTP session configuration for auth-service (Requirement 26.2).
 *
 * <p>Spring Session stores session data in Redis under the key namespace
 * {@code aisa:session:auth:} so that any auth-service instance can serve any
 * request without sticky sessions. This is the foundation for stateless
 * horizontal scaling — each instance reads/writes the same session store.
 *
 * <p>The {@code maxInactiveIntervalInSeconds} of 1800 (30 min) aligns with
 * typical idle session timeouts; the access-token TTL (15 min) is the primary
 * guard against stale authentication state.
 */
@Configuration
@EnableRedisHttpSession(
        redisNamespace = "aisa:session:auth",
        maxInactiveIntervalInSeconds = 1800
)
public class RedisSessionConfig {

    /**
     * General-purpose RedisTemplate for auth-service operational data
     * (e.g., lockout counters, token blacklist checks).
     * Key namespace convention: {@code aisa:auth:<purpose>:<key>}.
     */
    @Bean
    public RedisTemplate<String, Object> authRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
