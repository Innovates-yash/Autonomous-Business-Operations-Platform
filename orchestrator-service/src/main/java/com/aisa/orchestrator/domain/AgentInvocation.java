package com.aisa.orchestrator.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Records a single invocation of an AI agent within a {@link GenerationRun}.
 *
 * <p>Captures the agent type, its outcome status, attempt count, and timing
 * information (Requirements 6.4, 6.5, 6.9). A step may be retried up to 4 total
 * attempts (1 initial + 3 retries).
 */
@Entity
@Table(name = "agent_invocation")
public class AgentInvocation {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "generation_run_id", nullable = false)
    private GenerationRun generationRun;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", nullable = false, length = 32)
    private AgentType agentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private InvocationStatus status = InvocationStatus.PENDING;

    /** Number of attempts executed so far (max 4 per Requirement 6.5). */
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Error message recorded when the invocation fails or times out (Requirement 6.6). */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToOne(mappedBy = "invocation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private AgentOutput output;

    protected AgentInvocation() {
        // Required by JPA.
    }

    public AgentInvocation(GenerationRun generationRun, AgentType agentType) {
        this.generationRun = generationRun;
        this.agentType = agentType;
        this.status = InvocationStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // --- Getters and Setters ---

    public UUID getId() {
        return id;
    }

    public GenerationRun getGenerationRun() {
        return generationRun;
    }

    public void setGenerationRun(GenerationRun generationRun) {
        this.generationRun = generationRun;
    }

    public AgentType getAgentType() {
        return agentType;
    }

    public void setAgentType(AgentType agentType) {
        this.agentType = agentType;
    }

    public InvocationStatus getStatus() {
        return status;
    }

    public void setStatus(InvocationStatus status) {
        this.status = status;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public void incrementAttemptCount() {
        this.attemptCount++;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public AgentOutput getOutput() {
        return output;
    }

    public void setOutput(AgentOutput output) {
        this.output = output;
        if (output != null) {
            output.setInvocation(this);
        }
    }
}
