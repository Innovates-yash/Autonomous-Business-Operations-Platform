package com.aisa.agents.framework;

/**
 * Sealed result type returned by {@link SpecializedAgent#processResponse(String)}.
 *
 * <p>Enforces the complete-or-error contract (Requirement 7.1): every agent invocation
 * produces either a {@link Success} with non-null validated JSON content, or a
 * {@link Failure} with a non-null error message. No partial or mixed states are possible.
 */
public sealed interface AgentResult permits AgentResult.Success, AgentResult.Failure {

    /**
     * The agent produced a structurally valid output.
     *
     * @param validatedJson the validated JSON content to persist as the Design_Artifact
     */
    record Success(String validatedJson) implements AgentResult {
        public Success {
            if (validatedJson == null || validatedJson.isBlank()) {
                throw new IllegalArgumentException("validatedJson must not be null or blank");
            }
        }
    }

    /**
     * The agent failed to produce a valid output.
     *
     * @param errorMessage a human-readable description of what went wrong
     */
    record Failure(String errorMessage) implements AgentResult {
        public Failure {
            if (errorMessage == null || errorMessage.isBlank()) {
                throw new IllegalArgumentException("errorMessage must not be null or blank");
            }
        }
    }
}
