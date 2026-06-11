package com.aisa.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Stores the output produced by a successfully completed {@link AgentInvocation}.
 *
 * <p>The output content is the structured Design_Artifact payload produced by the
 * agent (Requirement 6.3). Persisted outputs survive instance restarts and prevent
 * re-processing of completed steps (Requirements 6.6, 26.6).
 */
@Entity
@Table(name = "agent_output")
public class AgentOutput {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invocation_id", nullable = false, unique = true)
    private AgentInvocation invocation;

    /** The structured Design_Artifact content (JSON). Stored as LONGTEXT for large payloads. */
    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "produced_at", nullable = false)
    private Instant producedAt;

    protected AgentOutput() {
        // Required by JPA.
    }

    public AgentOutput(AgentInvocation invocation, String content) {
        this.invocation = invocation;
        this.content = content;
        this.producedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (this.producedAt == null) {
            this.producedAt = Instant.now();
        }
    }

    // --- Getters and Setters ---

    public UUID getId() {
        return id;
    }

    public AgentInvocation getInvocation() {
        return invocation;
    }

    public void setInvocation(AgentInvocation invocation) {
        this.invocation = invocation;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getProducedAt() {
        return producedAt;
    }

    public void setProducedAt(Instant producedAt) {
        this.producedAt = producedAt;
    }
}
