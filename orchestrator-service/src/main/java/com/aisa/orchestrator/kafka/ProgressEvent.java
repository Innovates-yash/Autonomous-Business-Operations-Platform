package com.aisa.orchestrator.kafka;

import com.aisa.orchestrator.domain.AgentType;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-agent progress event published to the {@code agent-progress} Kafka topic
 * on each agent state change (Requirement 6.7).
 *
 * <p>The Notification Service consumes these events to fan out real-time
 * progress updates to Users over WebSocket (Requirement 22.1).
 *
 * <p>Event types:
 * <ul>
 *   <li>{@code STARTED} — agent invocation dispatched</li>
 *   <li>{@code SUCCESS} — agent produced a valid output</li>
 *   <li>{@code RETRY} — agent failed but will be retried (attempts remaining)</li>
 *   <li>{@code FAILED} — agent exhausted all attempts</li>
 *   <li>{@code TIMED_OUT} — agent exceeded the 120s timeout</li>
 *   <li>{@code SKIPPED} — agent halted because a prerequisite failed</li>
 * </ul>
 */
public record ProgressEvent(
        UUID generationRunId,
        UUID projectId,
        AgentType agentType,
        EventType eventType,
        int attemptCount,
        int maxAttempts,
        Instant timestamp,
        String errorMessage
) {

    public enum EventType {
        STARTED,
        SUCCESS,
        RETRY,
        FAILED,
        TIMED_OUT,
        SKIPPED
    }

    /**
     * Creates a STARTED event.
     */
    public static ProgressEvent started(UUID runId, UUID projectId, AgentType agent,
                                        int attempt, int maxAttempts) {
        return new ProgressEvent(runId, projectId, agent, EventType.STARTED,
                attempt, maxAttempts, Instant.now(), null);
    }

    /**
     * Creates a SUCCESS event.
     */
    public static ProgressEvent success(UUID runId, UUID projectId, AgentType agent,
                                        int attempt, int maxAttempts) {
        return new ProgressEvent(runId, projectId, agent, EventType.SUCCESS,
                attempt, maxAttempts, Instant.now(), null);
    }

    /**
     * Creates a RETRY event (agent failed but has attempts remaining).
     */
    public static ProgressEvent retry(UUID runId, UUID projectId, AgentType agent,
                                      int attempt, int maxAttempts, String error) {
        return new ProgressEvent(runId, projectId, agent, EventType.RETRY,
                attempt, maxAttempts, Instant.now(), error);
    }

    /**
     * Creates a FAILED event (all attempts exhausted).
     */
    public static ProgressEvent failed(UUID runId, UUID projectId, AgentType agent,
                                       int attempt, int maxAttempts, String error) {
        return new ProgressEvent(runId, projectId, agent, EventType.FAILED,
                attempt, maxAttempts, Instant.now(), error);
    }

    /**
     * Creates a TIMED_OUT event (120s exceeded, Req 6.4).
     */
    public static ProgressEvent timedOut(UUID runId, UUID projectId, AgentType agent,
                                         int attempt, int maxAttempts) {
        return new ProgressEvent(runId, projectId, agent, EventType.TIMED_OUT,
                attempt, maxAttempts, Instant.now(),
                "Agent did not respond within the timeout period");
    }

    /**
     * Creates a SKIPPED event (prerequisite agent failed, Req 6.6).
     */
    public static ProgressEvent skipped(UUID runId, UUID projectId, AgentType agent,
                                        String failedPrerequisite) {
        return new ProgressEvent(runId, projectId, agent, EventType.SKIPPED,
                0, 0, Instant.now(),
                "Halted: prerequisite agent " + failedPrerequisite + " failed");
    }
}
