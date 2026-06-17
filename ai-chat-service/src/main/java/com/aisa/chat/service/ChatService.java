package com.aisa.chat.service;

import com.aisa.chat.domain.ChatMessage;
import com.aisa.chat.domain.Conversation;
import com.aisa.chat.domain.MessageRole;
import com.aisa.chat.repository.ChatMessageRepository;
import com.aisa.chat.repository.ConversationRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core service for chat message handling (Requirements 5.1, 5.2, 5.9).
 * Validates content length (1–10,000 chars), rejects empty or over-limit messages
 * preserving the user's input in the error, associates user id and UTC timestamp.
 */
@Service
public class ChatService {

    private static final int MAX_CONTENT_LENGTH = 10_000;

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatService(ConversationRepository conversationRepository,
                       ChatMessageRepository chatMessageRepository) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
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
        return chatMessageRepository.save(message);
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
