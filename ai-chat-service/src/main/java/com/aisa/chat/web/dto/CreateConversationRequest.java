package com.aisa.chat.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request payload for creating a new conversation (Requirement 5.1, 5.9).
 * A conversation is scoped to a project.
 */
public record CreateConversationRequest(

        @NotNull(message = "projectId is required")
        UUID projectId
) {
}
