package com.aisa.chat.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request payload for POST /api/chat/messages (Requirements 5.1, 5.2, 5.9).
 * Accepts conversationId and content in a single body, with content validated
 * to be between 1 and 10,000 characters.
 */
public record SendMessageBodyRequest(

        @NotNull(message = "conversationId is required")
        UUID conversationId,

        @NotBlank(message = "content is required")
        @Size(min = 1, max = 10000, message = "content must be between 1 and 10000 characters")
        String content
) {
}
