package com.aisa.chat.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for ai-chat-service (Requirements 5.3, 26.2).
 *
 * <p>The ai-chat-service uses Redis to cache the 20 most recent messages per
 * conversation (the "context window") for fast assembly when constructing AI
 * requests. Full conversation history persists in MySQL; the rolling window in
 * Redis is a read-through cache rebuilt from MySQL on miss.
 *
 * <h2>Key namespace convention</h2>
 * <ul>
 *   <li>{@code aisa:chat:context:<projectId>} — list of the 20 most recent messages</li>
 *   <li>{@code aisa:chat:lock:<projectId>} — advisory lock for concurrent message appends</li>
 * </ul>
 *
 * <h2>TTL strategy</h2>
 * <p>Context window entries expire after 24 hours of inactivity. This balances memory
 * usage against the cost of rebuilding from MySQL. Active conversations rarely hit
 * expiry; idle conversations auto-evict.
 *
 * <h2>Stateless scaling</h2>
 * <p>Because the context window lives in shared Redis, any ai-chat-service replica
 * can serve any chat request without sticky sessions — enabling even load distribution
 * across instances (Requirement 26.2, 26.3).
 *
 * @see <a href="../../../docs/scalability/redis-usage-strategy.md">Redis Usage Strategy</a>
 */
@Configuration
public class RedisCacheConfig {

    /** Key prefix for all ai-chat-service Redis keys. */
    public static final String KEY_PREFIX = "aisa:chat:";

    /** Context window key prefix: {@code aisa:chat:context:<projectId>}. */
    public static final String CONTEXT_PREFIX = KEY_PREFIX + "context:";

    /** Advisory lock key prefix: {@code aisa:chat:lock:<projectId>}. */
    public static final String LOCK_PREFIX = KEY_PREFIX + "lock:";

    /** Default TTL for context window entries (24 hours). */
    public static final Duration CONTEXT_WINDOW_TTL = Duration.ofHours(24);

    /**
     * RedisTemplate for chat context-window operations.
     *
     * <p>Keys are plain strings ({@code aisa:chat:context:<projectId>}); values
     * are JSON-serialized chat messages. This template is used by the ChatService
     * to push/trim the rolling context window and read it back for AI requests.
     *
     * @param connectionFactory the auto-configured Redis connection factory
     * @return a configured RedisTemplate for chat context data
     */
    @Bean
    public RedisTemplate<String, Object> chatRedisTemplate(RedisConnectionFactory connectionFactory) {
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
