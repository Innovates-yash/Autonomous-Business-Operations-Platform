package com.aisa.project.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for answering a single clarifying question
 * (Requirement 4.4). Used by PUT /api/projects/{id}/questions/{qId}/answer.
 *
 * @param answer the answer text (1–5000 characters)
 */
public record AnswerQuestionRequest(
        @NotBlank(message = "Answer text is required")
        @Size(max = 5000, message = "Answer must be at most 5000 characters")
        String answer
) {
}
