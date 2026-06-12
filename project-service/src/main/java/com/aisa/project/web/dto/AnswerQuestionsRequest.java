package com.aisa.project.web.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import java.util.UUID;

/**
 * Request payload for answering clarifying questions. Maps question IDs to
 * answer text. At least one answer must be provided (Requirement 4.4).
 *
 * @param answers map of question ID to answer text
 */
public record AnswerQuestionsRequest(
        @NotEmpty(message = "At least one answer is required")
        Map<UUID, String> answers
) {
}
