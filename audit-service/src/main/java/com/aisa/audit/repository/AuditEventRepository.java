package com.aisa.audit.repository;

import com.aisa.audit.domain.AuditAction;
import com.aisa.audit.domain.AuditEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence gateway for {@link AuditEvent} records.
 *
 * <p>The backing {@code audit_event} table is append-only (Req 23.7): this
 * repository is used only to <em>insert</em> new events (via the inherited
 * {@code save}) and to <em>read</em> them back. No update or delete operation is
 * exposed, and the data layer additionally blocks UPDATE/DELETE so immutability
 * holds even if a caller attempts a mutation.
 *
 * <p>The {@link #search} query provides the user / action / time-range filtering
 * that backs the Admin-only query capability (Req 23.4). Each filter argument is
 * optional: a {@code null} value disables that predicate, so the same query
 * serves every combination of filters and returns an empty result set when
 * nothing matches (Req 23.5).
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /**
     * Returns audit events matching every supplied filter, most-recent first.
     *
     * @param userId   restrict to a single User identity, or {@code null} for any
     * @param action   restrict to a single action, or {@code null} for any
     * @param from     inclusive lower bound on {@code occurredAt}, or {@code null} for open-ended
     * @param to       inclusive upper bound on {@code occurredAt}, or {@code null} for open-ended
     * @param pageable paging / limit control
     * @return matching events ordered by {@code occurredAt} descending (may be empty)
     */
    @Query("""
            SELECT e FROM AuditEvent e
            WHERE (:userId IS NULL OR e.userId = :userId)
              AND (:action IS NULL OR e.action = :action)
              AND (:from IS NULL OR e.occurredAt >= :from)
              AND (:to IS NULL OR e.occurredAt <= :to)
            ORDER BY e.occurredAt DESC, e.id DESC
            """)
    List<AuditEvent> search(@Param("userId") String userId,
                            @Param("action") AuditAction action,
                            @Param("from") Instant from,
                            @Param("to") Instant to,
                            Pageable pageable);

    /**
     * Counts audit events whose timestamp is at or before the given instant.
     * Used by retention monitoring to assert that events older than the minimum
     * retention window are still present (Req 23.3).
     */
    long countByOccurredAtBefore(Instant cutoff);
}
