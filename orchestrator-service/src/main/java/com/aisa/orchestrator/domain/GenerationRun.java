package com.aisa.orchestrator.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a single end-to-end Blueprint generation run for a Project.
 *
 * <p>The orchestrator creates one {@code GenerationRun} per generation attempt,
 * records its status, and associates the per-agent {@link AgentInvocation}s
 * (Requirements 6.1, 6.9).
 */
@Entity
@Table(name = "generation_run")
public class GenerationRun {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** Cross-service reference to the Project being generated (database-per-service). */
    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private GenerationRunStatus status = GenerationRunStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "generationRun", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC")
    private List<AgentInvocation> invocations = new ArrayList<>();

    protected GenerationRun() {
        // Required by JPA.
    }

    public GenerationRun(UUID projectId) {
        this.projectId = projectId;
        this.status = GenerationRunStatus.PENDING;
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

    public UUID getProjectId() {
        return projectId;
    }

    public void setProjectId(UUID projectId) {
        this.projectId = projectId;
    }

    public GenerationRunStatus getStatus() {
        return status;
    }

    public void setStatus(GenerationRunStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<AgentInvocation> getInvocations() {
        return invocations;
    }

    /**
     * Associates an invocation with this run. Sets the back-reference automatically.
     */
    public void addInvocation(AgentInvocation invocation) {
        invocations.add(invocation);
        invocation.setGenerationRun(this);
    }
}
