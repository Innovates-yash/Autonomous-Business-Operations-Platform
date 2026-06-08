package com.aisa.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * An immutable, append-only audit record (Req 23).
 *
 * <p>Each event captures the User identity, the action performed, the target
 * identifier, and a UTC timestamp with millisecond precision (Req 23.1).
 *
 * <p>Immutability is enforced at several layers:
 * <ul>
 *   <li>Application layer: the entity exposes no setters and every persistent
 *       column is mapped with {@code updatable = false}, so JPA never emits an
 *       UPDATE for an existing row.</li>
 *   <li>Data layer: the Flyway migration creates a write-once table whose
 *       UPDATE/DELETE grants are revoked and that additionally blocks UPDATE and
 *       DELETE via triggers (Req 23.7).</li>
 * </ul>
 *
 * <p>The backing table is therefore append-only: rows may only be inserted and
 * read, never modified or removed.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    /** Identity of the User that performed the action (Req 23.1). */
    @Column(name = "user_id", updatable = false, nullable = false, length = 254)
    private String userId;

    /** The action performed (Req 23.1). */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", updatable = false, nullable = false, length = 64)
    private AuditAction action;

    /** Identifier of the entity the action targeted (Req 23.1). */
    @Column(name = "target_id", updatable = false, nullable = false, length = 255)
    private String targetId;

    /** Event time, recorded in UTC with millisecond precision (Req 23.1). */
    @Column(name = "occurred_at", updatable = false, nullable = false)
    private Instant occurredAt;

    /** Optional correlation identifier propagated across services (Req 27). */
    @Column(name = "correlation_id", updatable = false, length = 64)
    private String correlationId;

    /** Required by JPA; not for application use. */
    protected AuditEvent() {
    }

    public AuditEvent(String userId, AuditAction action, String targetId,
                      Instant occurredAt, String correlationId) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.action = Objects.requireNonNull(action, "action");
        this.targetId = Objects.requireNonNull(targetId, "targetId");
        // Normalize to millisecond precision so persisted timestamps match the
        // contract regardless of the source clock's resolution (Req 23.1).
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt")
                .truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
        this.correlationId = correlationId;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getTargetId() {
        return targetId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuditEvent other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
