package com.aisa.notification.messaging;

import java.time.Instant;

/**
 * Kafka event representing a project lifecycle state transition (Req 3.8, 22.2).
 *
 * <p>Published by the project service whenever a project transitions between states,
 * consumed by the notification service for WebSocket fan-out to subscribed clients.
 *
 * @param projectId    the project whose state changed
 * @param userId       the user who initiated the transition (fan-out target)
 * @param previousState the state before transition
 * @param newState      the state after transition
 * @param timestamp     when the transition occurred
 * @param correlationId trace correlation identifier
 */
public record ProjectStateChangeEvent(
        String projectId,
        String userId,
        String previousState,
        String newState,
        Instant timestamp,
        String correlationId
) {}
