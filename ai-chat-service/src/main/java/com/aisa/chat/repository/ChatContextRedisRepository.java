package com.aisa.chat.repository;

import com.aisa.chat.config.RedisCacheConfig;
import com.aisa.chat.domain.ChatMessage;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Redis-backed repository for the 20-message rolling context window (Requirements 5.3, 26.2).
 *
 * <p>The AI Chat Interface must include the 20 most recent prior messages as context
 * for subsequent responses. Rather than querying MySQL on every chat request, this
 * repository maintains a rolling window in Redis under:
 * {@code aisa:chat:context:<projectId>}
 *
 * <p>Operations:
 * <ul>
 *   <li>{@link #pushMessage(UUID, ChatMessage)} — appends a message and trims to the window size</li>
 *   <li>{@link #getContextWindow(UUID)} — retrieves the current context window for AI requests</li>
 *   <li>{@link #evict(UUID)} — removes the context window (used on conversation reset)</li>
 * </ul>
 *
 * <h2>Stateless scaling</h2>
 * <p>Because the context window lives in shared Redis, any ai-chat-service replica
 * can assemble the context for any project without sticky sessions — enabling even
 * load distribution across instances (Requirement 26.2, 26.3). Under 100 concurrent
 * blueprint generations, project creation remains ≤5s because chat context reads are
 * served from Redis without contending on MySQL (Requirement 26.4).
 *
 * <h2>TTL and eviction</h2>
 * <p>Context window entries expire after 24 hours of inactivity. Active conversations
 * reset the TTL on each push. On cache miss, the window is rebuilt from MySQL via
 * {@link ChatMessageRepository}.
 */
@Repository
public class ChatContextRedisRepository {

    private final RedisTemplate<String, Object> chatRedisTemplate;
    private final int contextWindowSize;

    public ChatContextRedisRepository(
            RedisTemplate<String, Object> chatRedisTemplate,
            @Value("${aisa.chat.context-window-size:20}") int contextWindowSize) {
        this.chatRedisTemplate = chatRedisTemplate;
        this.contextWindowSize = contextWindowSize;
    }

    /**
     * Appends a message to the project's context window and trims to the configured
     * window size (default 20). Resets the TTL on each push so active conversations
     * don't expire.
     *
     * @param projectId the project identifier
     * @param message   the chat message to append
     */
    public void pushMessage(UUID projectId, ChatMessage message) {
        String key = contextKey(projectId);
        ListOperations<String, Object> ops = chatRedisTemplate.opsForList();

        // Append to the right end of the list
        ops.rightPush(key, message);

        // Trim to keep only the most recent N messages (0-indexed: keep [size - windowSize, -1])
        Long size = ops.size(key);
        if (size != null && size > contextWindowSize) {
            ops.trim(key, size - contextWindowSize, -1);
        }

        // Reset TTL on activity
        chatRedisTemplate.expire(key, RedisCacheConfig.CONTEXT_WINDOW_TTL.toSeconds(), TimeUnit.SECONDS);
    }

    /**
     * Retrieves the current context window for the given project.
     *
     * @param projectId the project identifier
     * @return the list of recent messages (up to window size), or empty list on cache miss
     */
    @SuppressWarnings("unchecked")
    public List<ChatMessage> getContextWindow(UUID projectId) {
        String key = contextKey(projectId);
        ListOperations<String, Object> ops = chatRedisTemplate.opsForList();

        List<Object> raw = ops.range(key, 0, -1);
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }

        return raw.stream()
                .map(obj -> (ChatMessage) obj)
                .toList();
    }

    /**
     * Evicts the context window for the given project (e.g., on conversation reset).
     *
     * @param projectId the project identifier
     */
    public void evict(UUID projectId) {
        chatRedisTemplate.delete(contextKey(projectId));
    }

    /**
     * Rebuilds the context window from a list of messages (used on cache miss
     * when the window must be populated from MySQL).
     *
     * @param projectId the project identifier
     * @param messages  the most recent messages to cache (should be ≤ window size)
     */
    public void rebuild(UUID projectId, List<ChatMessage> messages) {
        String key = contextKey(projectId);

        // Delete existing and repopulate
        chatRedisTemplate.delete(key);

        if (messages == null || messages.isEmpty()) {
            return;
        }

        ListOperations<String, Object> ops = chatRedisTemplate.opsForList();
        for (ChatMessage msg : messages) {
            ops.rightPush(key, msg);
        }

        // Set TTL
        chatRedisTemplate.expire(key, RedisCacheConfig.CONTEXT_WINDOW_TTL.toSeconds(), TimeUnit.SECONDS);
    }

    private String contextKey(UUID projectId) {
        return RedisCacheConfig.CONTEXT_PREFIX + projectId.toString();
    }
}
