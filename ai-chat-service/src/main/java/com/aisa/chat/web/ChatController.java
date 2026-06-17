package com.aisa.chat.web;

import com.aisa.chat.domain.ChatMessage;
import com.aisa.chat.domain.Conversation;
import com.aisa.chat.service.ChatService;
import com.aisa.chat.web.dto.ChatMessageResponse;
import com.aisa.chat.web.dto.ConversationResponse;
import com.aisa.chat.web.dto.CreateConversationRequest;
import com.aisa.chat.web.dto.SendMessageRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for chat conversations and messages (Requirements 5.1, 5.2, 5.9).
 *
 * <ul>
 *   <li>POST /api/chat/conversations — create a new conversation</li>
 *   <li>POST /api/chat/{conversationId}/messages — send a message to an existing conversation</li>
 * </ul>
 *
 * Validates content length (1–10,000 chars), rejects empty/over-limit messages
 * with field-level errors preserving the user's input, associates the userId
 * from the X-User-Id header and a UTC timestamp.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Creates a new conversation for the authenticated user within a project.
     *
     * @param userId  authenticated user id from gateway header
     * @param request validated request body with projectId
     * @return the created conversation with 201 Created status
     */
    @PostMapping("/conversations")
    public ResponseEntity<ConversationResponse> createConversation(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateConversationRequest request) {

        Conversation saved = chatService.createConversation(
                UUID.fromString(userId),
                request.projectId());

        ConversationResponse response = new ConversationResponse(
                saved.getId(),
                saved.getUserId(),
                saved.getProjectId(),
                saved.getCreatedAt());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Submits a chat message within an existing conversation. The userId is
     * extracted from the X-User-Id header (set by the API Gateway after JWT
     * validation). Content is validated to be between 1 and 10,000 chars.
     *
     * @param userId         authenticated user id from gateway header
     * @param conversationId the conversation to add the message to
     * @param request        validated request body with content
     * @return the persisted message with 201 Created status
     */
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable UUID conversationId,
            @Valid @RequestBody SendMessageRequest request) {

        ChatMessage saved = chatService.sendMessage(
                conversationId,
                UUID.fromString(userId),
                request.content());

        ChatMessageResponse response = new ChatMessageResponse(
                saved.getId(),
                saved.getConversation().getId(),
                saved.getRole().name(),
                saved.getContent(),
                saved.getUserId(),
                saved.getCreatedAt());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
