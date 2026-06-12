package com.aisa.project.repository;

import com.aisa.project.domain.Requirement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link Requirement} entities. Supports retrieval scoped to a
 * Project for manual edits while in Analyzing state (Requirement 4.8).
 */
@Repository
public interface RequirementRepository extends JpaRepository<Requirement, UUID> {

    /** All requirements for a project, ordered by creation time. */
    List<Requirement> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    /** Single requirement scoped to a project (ownership guard). */
    Optional<Requirement> findByIdAndProjectId(UUID id, UUID projectId);
}
