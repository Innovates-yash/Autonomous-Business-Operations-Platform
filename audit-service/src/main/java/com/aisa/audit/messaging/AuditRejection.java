package com.aisa.audit.messaging;

import com.aisa.audit.domain.AuditAction;
import java.time.Instant;

/**
 * Signal published to the {@code audit-rejections} topic when an audit event
 * could not be durably recorded after exhausting all retry attempts (Req 23.2).
 *
 * <p>This realizes the audit-or-abort guarantee (correctness Property 21): a
 * security-relevant action either has its audit event durably recorded, or the
 * originating action is rejected. The originating service consumes this signal,
 * keyed by {@link #actionId}, and rejects/rolls back the corresponding action
 * while preserving prior system state.
 *
 * @param actionId      identifier of the originating action to reject (Req 23.2)
 * @param userId        identity carried on the failed audit request
 * @param action        the action whose audit recording failed
 * @param targetId      target identifier carried on the failed audit request
 * @param errorCode     stable error code identifying the failed audit operation
 * @param reason        client-safe description of the failure
 * @param attempts      number of recording attempts that were made
 * @param rejectedAt    time the rejection signal was produced
 * @param correlationId correlation identifier propagated across services (Req 27)
 */
public record AuditRejection(
        String actionId,
        String userId,
        AuditAction action,
        String targetId,
        String errorCode,
        String reason,
        int attempts,
        Instant rejectedAt,
        String correlationId) {
}
