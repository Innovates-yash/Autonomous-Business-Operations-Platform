package com.aisa.chat.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request payload for sending a chat message (Requirements 5.1, 5.2).
 * Content must be between 1 and 10,000 characters.
 * The projectId is used to find or create the conversation for this user+project pair.
 */
public record SendMessageRequest(

        @NotNull(message = "projectId is required")
        UUID projectId,

        @NotBlank(message = "content is required")
        @Size(min = 1, max = 10000, message = "content must be between 1 and 10000 characters")
        String content
) {
}
