package com.aisa.chat.web;

import com.aisa.chat.domain.ChatMessage;
import com.aisa.chat.service.ChatService;
import com.aisa.chat.web.dto.ChatMessageResponse;
import com.aisa.chat.web.dto.SendMessageRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for submitting chat messages (Requirements 5.1, 5.2, 5.9).
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
     * Submits a chat message within a project conversation.
     * The userId is extracted from the X-User-Id header (set by the API Gateway
     * after JWT validation). Content is validated to be between 1 and 10,000 chars.
     */
    @PostMapping("/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody SendMessageRequest request) {

        ChatMessage saved = chatService.sendMessage(
                UUID.fromString(userId),
                request.projectId(),
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
