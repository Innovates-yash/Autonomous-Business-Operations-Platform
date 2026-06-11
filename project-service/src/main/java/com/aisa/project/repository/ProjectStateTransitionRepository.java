package com.aisa.project.repository;

import com.aisa.project.domain.ProjectStateTransition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link ProjectStateTransition} records.
 *
 * <p>Transition records are immutable audit entries (Requirement 3.8): every lifecycle
 * state change is recorded with its timestamp and the initiating User. Retention is
 * handled at the persistence level; no application-level deletion is exposed.
 */
@Repository
public interface ProjectStateTransitionRepository extends JpaRepository<ProjectStateTransition, UUID> {

    /**
     * Returns all transitions for a given Project ordered by occurrence time ascending.
     * Used for audit trail and lifecycle visualization.
     *
     * @param projectId the Project identifier
     * @return the chronologically ordered list of transitions
     */
    List<ProjectStateTransition> findByProjectIdOrderByOccurredAtAsc(UUID projectId);
}
