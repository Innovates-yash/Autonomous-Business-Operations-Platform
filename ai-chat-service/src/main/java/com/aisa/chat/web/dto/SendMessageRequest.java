package com.aisa.chat.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for sending a chat message (Requirements 5.1, 5.2).
 * Content must be between 1 and 10,000 characters. The conversationId is
 * provided in the URL path.
 */
public record SendMessageRequest(

        @NotBlank(message = "content is required")
        @Size(min = 1, max = 10000, message = "content must be between 1 and 10000 characters")
        String content
) {
}
