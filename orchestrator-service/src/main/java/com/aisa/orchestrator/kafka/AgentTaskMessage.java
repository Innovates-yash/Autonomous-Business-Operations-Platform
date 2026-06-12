package com.aisa.orchestrator.kafka;

import com.aisa.orchestrator.domain.AgentType;

import java.util.Map;
import java.util.UUID;

/**
 * Message payload submitted to the {@code agent-tasks} Kafka topic.
 *
 * <p>Each message represents a single agent invocation request that an agent worker
 * consumes. It carries the generation run context, the target agent, and the prerequisite
 * outputs that the agent needs as input (Requirement 6.1, 6.2).
 *
 * @param generationRunId the owning generation run
 * @param invocationId    the specific invocation record for tracking
 * @param projectId       the project being generated
 * @param agentType       the agent to invoke
 * @param prerequisiteOutputs map of prerequisite agent type to their output content (Req 6.2)
 */
public record AgentTaskMessage(
        UUID generationRunId,
        UUID invocationId,
        UUID projectId,
        AgentType agentType,
        Map<AgentType, String> prerequisiteOutputs
) {
}
