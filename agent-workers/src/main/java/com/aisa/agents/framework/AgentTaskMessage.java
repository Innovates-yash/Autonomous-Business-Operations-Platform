package com.aisa.agents.framework;

import java.util.Map;
import java.util.UUID;

/**
 * Message payload consumed from the {@code agent-tasks} Kafka topic.
 *
 * <p>Each message represents a single agent invocation request dispatched by the
 * Orchestrator Service. The agent worker consumes this, routes it to the appropriate
 * {@link SpecializedAgent}, and produces an {@link AgentCompletionEvent} on the
 * {@code agent-progress} topic (Requirements 6.1, 6.2).
 *
 * @param generationRunId     the owning generation run
 * @param invocationId        the specific invocation record for tracking
 * @param projectId           the project being generated
 * @param agentType           the target agent type name (maps to {@link SpecializedAgent#agentType()})
 * @param prerequisiteOutputs map of prerequisite agent type names to their output content (Req 6.2)
 */
public record AgentTaskMessage(
        UUID generationRunId,
        UUID invocationId,
        UUID projectId,
        String agentType,
        Map<String, String> prerequisiteOutputs
) {
}
