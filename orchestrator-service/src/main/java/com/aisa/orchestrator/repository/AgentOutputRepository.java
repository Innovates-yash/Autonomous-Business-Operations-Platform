package com.aisa.orchestrator.repository;

import com.aisa.orchestrator.domain.AgentOutput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link AgentOutput} persistence.
 *
 * <p>Persisted outputs allow safe re-queue: if an orchestrator instance crashes,
 * another instance can resume from the last persisted state without re-processing
 * completed steps (Requirements 6.6, 26.6).
 */
@Repository
public interface AgentOutputRepository extends JpaRepository<AgentOutput, UUID> {

    Optional<AgentOutput> findByInvocationId(UUID invocationId);
}
