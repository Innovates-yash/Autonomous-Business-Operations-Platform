package com.aisa.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** The agent type that produced this output, denormalized for efficient querying. */
    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", nullable = false, length = 32)
    private AgentType agentType;

    /** The structured Design_Artifact content (JSON). Stored as LONGTEXT for large payloads. */
    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /** Version counter for the output, supporting iterative refinement across generation runs. */
    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "produced_at", nullable = false)
    private Instant producedAt;

    protected AgentOutput() {
        // Required by JPA.
    }

    public AgentOutput(AgentInvocation invocation, String content) {
        this.invocation = invocation;
        this.agentType = invocation.getAgentType();
        this.content = content;
        this.producedAt = Instant.now();
    }

    @PrePersist
    void onCreate() {
        if (this.producedAt == null) {
            this.producedAt = Instant.now();
        }
        if (this.agentType == null && this.invocation != null) {
            this.agentType = this.invocation.getAgentType();
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

    public AgentType getAgentType() {
        return agentType;
    }

    public void setAgentType(AgentType agentType) {
        this.agentType = agentType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Instant getProducedAt() {
        return producedAt;
    }

    public void setProducedAt(Instant producedAt) {
        this.producedAt = producedAt;
    }
}
