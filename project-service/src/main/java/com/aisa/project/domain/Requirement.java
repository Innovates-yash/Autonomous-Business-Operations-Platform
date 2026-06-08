package com.aisa.project.domain;

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
 * A single structured requirement derived from a {@link Project}'s {@link Idea}.
 * Each requirement is expressed as one declarative statement and labelled with
 * exactly one {@link RequirementType} classification (Requirements 4.2, 7.1, 7.2).
 * A recommended assumption may be recorded when the source Idea has a gap
 * (Requirement 7.3).
 */
@Entity
@Table(name = "requirement")
public class Requirement {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "statement", nullable = false, length = 2000)
    private String statement;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private RequirementType type;

    @Column(name = "recommended_assumption", length = 2000)
    private String recommendedAssumption;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Requirement() {
        // Required by JPA.
    }

    public Requirement(Project project, String statement, RequirementType type) {
        this.project = project;
        this.statement = statement;
        this.type = type;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
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

    public String getStatement() {
        return statement;
    }

    public void setStatement(String statement) {
        this.statement = statement;
    }

    public RequirementType getType() {
        return type;
    }

    public void setType(RequirementType type) {
        this.type = type;
    }

    public String getRecommendedAssumption() {
        return recommendedAssumption;
    }

    public void setRecommendedAssumption(String recommendedAssumption) {
        this.recommendedAssumption = recommendedAssumption;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
