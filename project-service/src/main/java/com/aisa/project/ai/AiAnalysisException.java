package com.aisa.project.ai;

/**
 * Thrown when the AI provider fails to produce a valid analysis result after
 * exhausting all retries (Requirement 4.9). The Requirement Analysis Module
 * must halt analysis, preserve the Project's prior state and any existing
 * requirements, and surface this error to the user.
 */
public class AiAnalysisException extends RuntimeException {

    public AiAnalysisException(String message) {
        super(message);
    }

    public AiAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
