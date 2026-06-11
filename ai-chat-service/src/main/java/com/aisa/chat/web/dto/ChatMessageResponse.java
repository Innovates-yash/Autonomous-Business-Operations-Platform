package com.aisa.chat.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing a persisted chat message (Requirement 5.9).
 */
public record ChatMessageResponse(
        UUID id,
        UUID conversationId,
        String role,
        String content,
        UUID userId,
        Instant createdAt
) {
}
