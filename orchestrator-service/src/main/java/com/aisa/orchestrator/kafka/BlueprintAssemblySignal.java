package com.aisa.orchestrator.kafka;

import java.time.Instant;
import java.util.UUID;

/**
 * Signal published to Kafka when all agents have completed successfully,
 * instructing the Blueprint Service to assemble the Blueprint (Requirement 6.8).
 *
 * <p>The Blueprint Service consumes this event and assembles all
 * {@code AgentOutput}s into a single versioned Blueprint within 60 seconds
 * (Requirement 18.1).
 */
public record BlueprintAssemblySignal(
        UUID generationRunId,
        UUID projectId,
        Instant completedAt
) {

    /**
     * Creates an assembly signal for the given generation run.
     */
    public static BlueprintAssemblySignal of(UUID generationRunId, UUID projectId) {
        return new BlueprintAssemblySignal(generationRunId, projectId, Instant.now());
    }
}
