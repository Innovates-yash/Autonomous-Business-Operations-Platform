package com.aisa.audit.query;

import com.aisa.audit.domain.AuditAction;
import java.time.Instant;

/**
 * The optional filter criteria for an Admin audit query (Req 23.4). Every field
 * is optional: a {@code null} value disables that predicate, so any combination
 * of User identity, action, and time range is supported and an unmatched filter
 * yields an empty result set (Req 23.5).
 *
 * @param userId restrict to a single User identity, or {@code null} for any
 * @param action restrict to a single action, or {@code null} for any
 * @param from   inclusive lower bound on the event timestamp, or {@code null} for open-ended
 * @param to     inclusive upper bound on the event timestamp, or {@code null} for open-ended
 */
public record AuditQueryFilter(String userId, AuditAction action, Instant from, Instant to) {
}
