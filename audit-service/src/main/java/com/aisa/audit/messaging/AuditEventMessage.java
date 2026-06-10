package com.aisa.audit.messaging;

import com.aisa.audit.domain.AuditAction;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;

/**
 * Wire contract for an audit-event request consumed from the {@code audit-events}
 * Kafka topic (Req 23.1).
 *
 * <p>Originating services (Auth, Project, Blueprint, ...) publish one message per
 * security- or change-relevant action they perform. The Audit_Service consumes
 * the message and durably records an {@link com.aisa.audit.domain.AuditEvent}.
 *
 * <p>Unknown JSON properties are ignored so producers may evolve the message
 * with additive fields without breaking this consumer.
 *
 * @param actionId      identifier of the originating action; echoed back on a
 *                      rejection so the originating service can roll back / fail
 *                      the correct action (Req 23.2)
 * @param userId        identity of the User that performed the action (Req 23.1)
 * @param action        the action performed (Req 23.1)
 * @param targetId      identifier of the entity the action targeted (Req 23.1)
 * @param occurredAt    event time; recorded in UTC with millisecond precision (Req 23.1)
 * @param correlationId optional correlation identifier propagated across services (Req 27)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditEventMessage(
        String actionId,
        String userId,
        AuditAction action,
        String targetId,
        Instant occurredAt,
        String correlationId) {
}
