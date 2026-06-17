package com.aisa.chat.web.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload representing a persisted conversation (Requirement 5.1).
 */
public record ConversationResponse(
        UUID id,
        UUID userId,
        UUID projectId,
        Instant createdAt,
        Instant updatedAt
) {
}
