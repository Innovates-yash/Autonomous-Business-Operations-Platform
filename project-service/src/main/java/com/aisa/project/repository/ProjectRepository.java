package com.aisa.project.repository;

import com.aisa.project.domain.Project;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Project} aggregate.
 *
 * <p>View scoping (Requirement 3.6) is expressed through owner-scoped queries:
 * a non-privileged User sees only the Projects they own
 * ({@link #findByOwnerIdOrderByCreatedAtDesc(UUID)}), while privileged reviewer
 * roles use the inherited {@link #findAll()}. Single-Project access checks combine
 * {@link #findById(Object)} with the owning {@code ownerId} so that a Project a
 * User is not authorized to view is indistinguishable from one that does not exist
 * (Requirement 3.7).
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /** All Projects owned by the given User, newest first (owner-scoped list, Requirement 3.6). */
    List<Project> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    /** A single owned Project, used for ownership-scoped get-or-404 (Requirements 3.6, 3.7). */
    Optional<Project> findByIdAndOwnerId(UUID id, UUID ownerId);
}
