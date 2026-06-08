package com.aisa.project.domain;

import com.aisa.commons.domain.ProjectState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An immutable record of a single {@link Project} lifecycle state change,
 * capturing the originating and target states, the initiating User, and the
 * timestamp (Requirement 3.8). The first transition into {@link ProjectState#DRAFT}
 * at creation has a {@code null} {@link #fromState}.
 */
@Entity
@Table(name = "project_state_transition")
public class ProjectStateTransition {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_state", length = 32)
    private ProjectState fromState;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_state", nullable = false, length = 32)
    private ProjectState toState;

    /** Identifier of the User who initiated the transition (Requirement 3.8). */
    @Column(name = "initiated_by", nullable = false)
    private UUID initiatedBy;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected ProjectStateTransition() {
        // Required by JPA.
    }

    public ProjectStateTransition(Project project, ProjectState fromState, ProjectState toState, UUID initiatedBy) {
        this.project = project;
        this.fromState = fromState;
        this.toState = toState;
        this.initiatedBy = initiatedBy;
    }

    @PrePersist
    void onCreate() {
        this.occurredAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public ProjectState getFromState() {
        return fromState;
    }

    public ProjectState getToState() {
        return toState;
    }

    public UUID getInitiatedBy() {
        return initiatedBy;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
