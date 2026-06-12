package com.aisa.chat.service;

import com.aisa.chat.domain.ChatMessage;
import com.aisa.chat.domain.Conversation;
import com.aisa.chat.domain.MessageRole;
import com.aisa.chat.repository.ChatMessageRepository;
import com.aisa.chat.repository.ConversationRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core service for chat message handling (Requirements 5.1, 5.2, 5.9).
 * Validates content, looks up the Conversation by id, persists the message
 * with a UTC timestamp, and associates the user id.
 */
@Service
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatService(ConversationRepository conversationRepository,
                       ChatMessageRepository chatMessageRepository) {
        this.conversationRepository = conversationRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * Persists a user message in the given conversation.
     *
     * @param userId         the authenticated user's identifier (from X-User-Id header)
     * @param conversationId the conversation to post the message to
     * @param content        validated content (1–10,000 chars)
     * @return the persisted ChatMessage
     * @throws jakarta.persistence.EntityNotFoundException if conversation does not exist
     */
    @Transactional
    public ChatMessage sendMessage(UUID userId, UUID conversationId, String content) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Conversation not found: " + conversationId));

        ChatMessage message = new ChatMessage(conversation, MessageRole.USER, content, userId);
        return chatMessageRepository.save(message);
    }
}
