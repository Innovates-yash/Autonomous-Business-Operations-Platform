package com.aisa.project.service;

import java.util.UUID;

/**
 * Thrown when a ClarifyingQuestion is not found within the expected Project scope.
 */
public class QuestionNotFoundException extends RuntimeException {

    private final UUID questionId;

    public QuestionNotFoundException(UUID questionId) {
        super("Clarifying question not found: " + questionId);
        this.questionId = questionId;
    }

    public UUID getQuestionId() {
        return questionId;
    }
}
