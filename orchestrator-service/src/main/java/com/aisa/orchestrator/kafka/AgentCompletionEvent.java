package com.aisa.orchestrator.kafka;

import com.aisa.orchestrator.domain.AgentType;

import java.time.Instant;
import java.util.UUID;

/**
 * Event received on the {@code agent-progress} Kafka topic when an agent worker
 * completes (successfully or with failure).
 *
 * <p>The orchestrator consumes this event to mark the corresponding
 * {@link com.aisa.orchestrator.domain.AgentInvocation} as complete, persist the output,
 * and trigger the next wave of ready agents (Requirements 6.1, 6.9).
 *
 * @param generationRunId the owning generation run
 * @param invocationId    the invocation that completed
 * @param agentType       the agent that completed
 * @param success         whether the agent produced a valid output
 * @param outputContent   the Design_Artifact payload (null if failed)
 * @param completedAt     when the agent finished
 * @param errorMessage    error description if failed (null if success)
 */
public record AgentCompletionEvent(
        UUID generationRunId,
        UUID invocationId,
        AgentType agentType,
        boolean success,
        String outputContent,
        Instant completedAt,
        String errorMessage
) {
}
