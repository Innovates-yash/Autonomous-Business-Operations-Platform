package com.aisa.orchestrator.repository;

import com.aisa.orchestrator.domain.GenerationRun;
import com.aisa.orchestrator.domain.GenerationRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link GenerationRun} persistence.
 */
@Repository
public interface GenerationRunRepository extends JpaRepository<GenerationRun, UUID> {

    Optional<GenerationRun> findByProjectIdAndStatus(UUID projectId, GenerationRunStatus status);

    List<GenerationRun> findByStatus(GenerationRunStatus status);
}
