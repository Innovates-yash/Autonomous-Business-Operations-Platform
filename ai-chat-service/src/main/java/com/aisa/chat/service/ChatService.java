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
 * Validates content, finds or creates a Conversation for the user+project pair,
 * persists the message with a UTC timestamp, and associates the user id.
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
     * Persists a user message. If no conversation exists for the given user+project,
     * one is created automatically.
     *
     * @param userId    the authenticated user's identifier (from X-User-Id header)
     * @param projectId the project to which this message belongs
     * @param content   validated content (1–10,000 chars)
     * @return the persisted ChatMessage
     */
    @Transactional
    public ChatMessage sendMessage(UUID userId, UUID projectId, String content) {
        Conversation conversation = conversationRepository.findByUserIdAndProjectId(userId, projectId)
                .orElseGet(() -> conversationRepository.save(new Conversation(userId, projectId)));

        ChatMessage message = new ChatMessage(conversation, MessageRole.USER, content, userId);
        return chatMessageRepository.save(message);
    }
}
