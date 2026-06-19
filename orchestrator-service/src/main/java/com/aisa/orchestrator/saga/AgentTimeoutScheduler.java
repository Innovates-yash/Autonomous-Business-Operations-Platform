package com.aisa.orchestrator.saga;

import com.aisa.orchestrator.domain.AgentInvocation;
import com.aisa.orchestrator.domain.GenerationRun;
import com.aisa.orchestrator.domain.GenerationRunStatus;
import com.aisa.orchestrator.domain.InvocationStatus;
import com.aisa.orchestrator.repository.AgentInvocationRepository;
import com.aisa.orchestrator.repository.GenerationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled component that monitors RUNNING agent invocations for timeouts
 * (Requirement 6.4).
 *
 * <p>Runs every 15 seconds, scanning for {@link InvocationStatus#RUNNING}
 * invocations whose {@code startedAt} exceeds the configurable timeout
 * (default 120 seconds). Stalled invocations are marked as
 * {@link InvocationStatus#TIMED_OUT} and fed back into the
 * {@link SagaExecutionService#handleTimeout} retry/failure flow.
 *
 * <p>Only invocations belonging to {@link GenerationRunStatus#RUNNING} runs
 * are eligible, preventing interference with already-completed or failed runs.
 */
@Component
public class AgentTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(AgentTimeoutScheduler.class);

    private final AgentInvocationRepository agentInvocationRepository;
    private final GenerationRunRepository generationRunRepository;
    private final SagaExecutionService sagaExecutionService;
    private final long timeoutSeconds;

    public AgentTimeoutScheduler(
            AgentInvocationRepository agentInvocationRepository,
            GenerationRunRepository generationRunRepository,
            SagaExecutionService sagaExecutionService,
            @Value("${aisa.orchestrator.agent-timeout-seconds:120}") long timeoutSeconds) {
        this.agentInvocationRepository = agentInvocationRepository;
        this.generationRunRepository = generationRunRepository;
        this.sagaExecutionService = sagaExecutionService;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Periodically scans for stalled RUNNING invocations and times them out.
     *
     * <p>Fixed delay of 15 seconds ensures timely detection without excessive
     * database polling. The initial delay of 30 seconds allows the application
     * to fully start before scanning.
     */
    @Scheduled(fixedDelayString = "${aisa.orchestrator.timeout-scan-interval-ms:15000}",
               initialDelayString = "${aisa.orchestrator.timeout-scan-initial-delay-ms:30000}")
    @Transactional
    public void scanForTimedOutInvocations() {
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(timeoutSeconds));

        List<AgentInvocation> stalled = agentInvocationRepository
                .findByStatusAndStartedAtBefore(InvocationStatus.RUNNING, cutoff);

        if (stalled.isEmpty()) {
            return;
        }

        log.info("Found {} stalled RUNNING invocations past {}s timeout", stalled.size(), timeoutSeconds);

        for (AgentInvocation invocation : stalled) {
            processTimedOutInvocation(invocation);
        }
    }

    private void processTimedOutInvocation(AgentInvocation invocation) {
        GenerationRun run = invocation.getGenerationRun();

        // Only process invocations belonging to active (RUNNING) generation runs
        if (run.getStatus() != GenerationRunStatus.RUNNING) {
            log.debug("Skipping timed-out invocation {} — run {} is in state {}",
                    invocation.getId(), run.getId(), run.getStatus());
            return;
        }

        try {
            sagaExecutionService.handleTimeout(run, invocation);
        } catch (Exception e) {
            log.error("Error handling timeout for agent {} in run={}: {}",
                    invocation.getAgentType(), run.getId(), e.getMessage(), e);
        }
    }

    /**
     * Returns the configured timeout in seconds (for testing visibility).
     */
    long getTimeoutSeconds() {
        return timeoutSeconds;
    }
}
