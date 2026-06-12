package com.aisa.project.web.dto;

import com.aisa.project.domain.ClarifyingQuestion;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for a clarifying question, including its reference to a specific
 * requirement when applicable (Requirement 4.3).
 */
public record ClarifyingQuestionResponse(
        UUID id,
        String question,
        String answer,
        UUID requirementId,
        Instant createdAt
) {

    public static ClarifyingQuestionResponse from(ClarifyingQuestion q) {
        return new ClarifyingQuestionResponse(
                q.getId(),
                q.getQuestion(),
                q.getAnswer(),
                q.getRequirement() != null ? q.getRequirement().getId() : null,
                q.getCreatedAt()
        );
    }
}
