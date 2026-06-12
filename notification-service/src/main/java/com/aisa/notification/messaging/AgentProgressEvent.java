package com.aisa.notification.messaging;

import java.time.Instant;

/**
 * Kafka event representing an agent progress state change during blueprint generation (Req 6.7, 22.1).
 *
 * <p>Published by the orchestrator on each agent state transition (start, success, retry, failure)
 * and consumed by the notification service for WebSocket fan-out to subscribed clients.
 *
 * @param projectId   the project undergoing generation
 * @param userId      the user who initiated generation (fan-out target)
 * @param agentId     identifier of the agent (e.g. "requirement-analyst")
 * @param status      agent step status: STARTED, SUCCESS, RETRY, FAILED, TIMED_OUT
 * @param attempt     current attempt number (1-based)
 * @param message     human-readable progress description
 * @param timestamp   when the event occurred
 * @param correlationId trace correlation identifier
 */
public record AgentProgressEvent(
        String projectId,
        String userId,
        String agentId,
        String status,
        int attempt,
        String message,
        Instant timestamp,
        String correlationId
) {}
