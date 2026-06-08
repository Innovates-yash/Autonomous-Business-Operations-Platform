package com.aisa.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A use case derived from a {@link Project}'s structured requirements. Each use
 * case references at least one functional {@link Requirement} it is derived from
 * (Requirements 4.5, 9.2). The reference is modelled as a many-to-many link so a
 * use case can span multiple requirements and a requirement can back several use
 * cases.
 */
@Entity
@Table(name = "use_case")
public class UseCase {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", nullable = false, length = 5000)
    private String description;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "use_case_requirement",
            joinColumns = @JoinColumn(name = "use_case_id"),
            inverseJoinColumns = @JoinColumn(name = "requirement_id"))
    private Set<Requirement> requirements = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UseCase() {
        // Required by JPA.
    }

    public UseCase(Project project, String title, String description) {
        this.project = project;
        this.title = title;
        this.description = description;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<Requirement> getRequirements() {
        return requirements;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
