package com.aisa.orchestrator.repository;

import com.aisa.orchestrator.domain.AgentInvocation;
import com.aisa.orchestrator.domain.AgentType;
import com.aisa.orchestrator.domain.InvocationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for {@link AgentInvocation} persistence.
 */
@Repository
public interface AgentInvocationRepository extends JpaRepository<AgentInvocation, UUID> {

    List<AgentInvocation> findByGenerationRunId(UUID generationRunId);

    Optional<AgentInvocation> findByGenerationRunIdAndAgentType(UUID generationRunId, AgentType agentType);

    List<AgentInvocation> findByGenerationRunIdAndStatus(UUID generationRunId, InvocationStatus status);

    /**
     * Finds all invocations with the given status whose start time is before the cutoff.
     * Used by {@link com.aisa.orchestrator.saga.AgentTimeoutScheduler} to detect stalled agents.
     */
    List<AgentInvocation> findByStatusAndStartedAtBefore(InvocationStatus status, Instant cutoff);
}
