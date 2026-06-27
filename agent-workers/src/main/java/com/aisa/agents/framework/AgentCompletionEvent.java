package com.aisa.agents.framework;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published to the {@code agent-progress} Kafka topic when an agent worker
 * completes processing (successfully or with failure).
 *
 * <p>The Orchestrator Service consumes this event to mark the corresponding
 * {@code AgentInvocation} as complete, persist the output, and trigger the next wave
 * of ready agents (Requirements 6.1, 6.9).
 *
 * <p>The complete-or-error contract (Requirement 7.1) guarantees that every event
 * has either {@code success=true} with non-null {@code outputContent}, or
 * {@code success=false} with non-null {@code errorMessage} — never a mixed state.
 *
 * @param generationRunId the owning generation run
 * @param invocationId    the invocation that completed
 * @param agentType       the agent type name that completed
 * @param success         whether the agent produced a valid output
 * @param outputContent   the Design_Artifact payload (null if failed)
 * @param completedAt     when the agent finished
 * @param errorMessage    error description if failed (null if success)
 */
public record AgentCompletionEvent(
        UUID generationRunId,
        UUID invocationId,
        String agentType,
        boolean success,
        String outputContent,
        Instant completedAt,
        String errorMessage
) {

    /**
     * Factory for a successful completion event.
     */
    public static AgentCompletionEvent success(UUID generationRunId, UUID invocationId,
                                                String agentType, String outputContent) {
        return new AgentCompletionEvent(
                generationRunId, invocationId, agentType, true,
                outputContent, Instant.now(), null
        );
    }

    /**
     * Factory for a failed completion event.
     */
    public static AgentCompletionEvent failure(UUID generationRunId, UUID invocationId,
                                                String agentType, String errorMessage) {
        return new AgentCompletionEvent(
                generationRunId, invocationId, agentType, false,
                null, Instant.now(), errorMessage
        );
    }
}
