package com.aisa.chat.service;

import com.aisa.chat.domain.ChatMessage;
import com.aisa.chat.domain.Conversation;
import com.aisa.chat.domain.MessageRole;
import com.aisa.chat.repository.ChatContextRedisRepository;
import com.aisa.chat.repository.ChatMessageRepository;
import com.aisa.chat.repository.ConversationRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core service for chat message handling (Requirements 5.1, 5.2, 5.3, 5.9).
 * Validates content length (1–10,000 chars), rejects empty or over-limit messages
 * preserving the user's input in the error, associates user id and UTC timestamp.
 *
 * <p>Maintains a 20-message rolling context window in Redis for fast assembly
 * when constructing AI requests (Requirement 5.3). On cache miss, the window is
 * rebuilt from MySQL. This enables stateless horizontal scaling (Req 26.2, 26.3).
 */
@Service
public class ChatService {

    private static final int MAX_CONTENT_LENGTH = 10_000;

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatContextRedisRepository chatContextRedisRepository;
    private final int contextWindowSize;

    public ChatService(ConversationRepository conversationRepository,
                       ChatMessageRepository chatMessageRepository,
                       ChatContextRedisRepository chatContextRedisRepository,
                       @Value("${aisa.chat.context-window-size:20}") int contextWindowSize) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatContextRedisRepository = chatContextRedisRepository;
        this.contextWindowSize = contextWindowSize;
    }

    /**
     * Creates a new conversation for the given user and project.
     *
     * @param userId    the authenticated user's identifier (from X-User-Id header)
     * @param projectId the project to which this conversation belongs
     * @return the persisted Conversation
     */
    @Transactional
    public Conversation createConversation(UUID userId, UUID projectId) {
        Conversation conversation = new Conversation(userId, projectId);
        return conversationRepository.save(conversation);
    }

    /**
     * Persists a user message within an existing conversation.
     * Validates content length 1–10,000 characters. Rejects empty and over-limit
     * content, preserving the user's input in the thrown exception.
     *
     * <p>After persisting in MySQL, the message is pushed to the Redis context
     * window for fast assembly on subsequent AI requests (Req 5.3, 26.2).
     *
     * @param conversationId the conversation to add the message to
     * @param userId         the authenticated user's identifier (from X-User-Id header)
     * @param content        message content (1–10,000 chars)
     * @return the persisted ChatMessage
     * @throws EntityNotFoundException   if the conversation does not exist
     * @throws InvalidMessageException   if content is empty or exceeds 10,000 chars
     */
    @Transactional
    public ChatMessage sendMessage(UUID conversationId, UUID userId, String content) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Conversation not found: " + conversationId));

        validateContent(content);

        ChatMessage message = new ChatMessage(conversation, MessageRole.USER, content, userId);
        ChatMessage saved = chatMessageRepository.save(message);

        // Push to Redis context window for fast retrieval on subsequent AI requests
        chatContextRedisRepository.pushMessage(conversation.getProjectId(), saved);

        return saved;
    }

    /**
     * Retrieves the 20 most recent messages for the given project's conversation,
     * used as context for AI requests (Requirement 5.3).
     *
     * <p>First attempts to read from the Redis context window cache. On cache miss,
     * falls back to MySQL and rebuilds the Redis cache for subsequent reads.
     *
     * @param projectId the project identifier
     * @return the list of recent messages (up to 20), ordered oldest-first
     */
    public List<ChatMessage> getContextWindow(UUID projectId) {
        // Try Redis first
        List<ChatMessage> cached = chatContextRedisRepository.getContextWindow(projectId);
        if (!cached.isEmpty()) {
            return cached;
        }

        // Cache miss — rebuild from MySQL
        List<ChatMessage> fromDb = chatMessageRepository
                .findByConversationProjectIdOrderByCreatedAtDesc(projectId, PageRequest.of(0, contextWindowSize));

        if (!fromDb.isEmpty()) {
            // Reverse to oldest-first order for context assembly
            List<ChatMessage> ordered = fromDb.reversed();
            chatContextRedisRepository.rebuild(projectId, ordered);
            return ordered;
        }

        return List.of();
    }

    /**
     * Validates message content length. Throws {@link InvalidMessageException} with
     * the user's original input preserved so the client doesn't lose their text.
     */
    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new InvalidMessageException("content must be between 1 and 10000 characters", content);
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new InvalidMessageException("content must be between 1 and 10000 characters", content);
        }
    }
}
