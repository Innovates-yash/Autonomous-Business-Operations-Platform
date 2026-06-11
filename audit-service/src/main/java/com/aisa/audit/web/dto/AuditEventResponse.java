package com.aisa.audit.web.dto;

import com.aisa.audit.domain.AuditAction;
import com.aisa.audit.domain.AuditEvent;
import java.time.Instant;

/**
 * Client-facing view of an {@link AuditEvent} returned by the Admin audit query
 * (Req 23.4). Carries only the recorded, non-sensitive fields of an audit event.
 *
 * @param id            the audit event's identifier
 * @param userId        identity of the User that performed the action
 * @param action        the action performed
 * @param targetId      identifier of the entity the action targeted
 * @param occurredAt    event time in UTC with millisecond precision
 * @param correlationId optional correlation identifier propagated across services
 */
public record AuditEventResponse(
        Long id,
        String userId,
        AuditAction action,
        String targetId,
        Instant occurredAt,
        String correlationId) {

    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getUserId(),
                event.getAction(),
                event.getTargetId(),
                event.getOccurredAt(),
                event.getCorrelationId());
    }
}
