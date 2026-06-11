package com.aisa.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for notification-service (Requirement 26.2).
 *
 * <p>The notification-service uses Redis to share WebSocket subscription state
 * across instances, enabling stateless horizontal scaling. When an instance
 * receives a Kafka event, it checks the shared subscription registry to determine
 * which connected clients should receive the fan-out.
 *
 * <h2>Key namespace convention</h2>
 * <ul>
 *   <li>{@code aisa:notify:subscriptions:<userId>} — set of active subscriptions</li>
 *   <li>{@code aisa:notify:fanout:<projectId>} — last-delivered event offset for resync</li>
 * </ul>
 *
 * <p>The {@code aisa:notify:} prefix isolates notification data from session
 * ({@code aisa:session:}), cache ({@code aisa:cache:}), and rate-limit
 * ({@code aisa:ratelimit:}) keys used by other services in the same Redis instance.
 */
@Configuration
public class RedisConfig {

    /** Key prefix for all notification-service Redis keys. */
    public static final String KEY_PREFIX = "aisa:notify:";

    @Bean
    public RedisTemplate<String, Object> notificationRedisTemplate(RedisConnectionFactory connectionFactory) {
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
