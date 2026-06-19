package com.aisa.orchestrator.saga;

import com.aisa.commons.kafka.KafkaTopics;
import com.aisa.orchestrator.config.AgentDependencyDag;
import com.aisa.orchestrator.domain.AgentInvocation;
import com.aisa.orchestrator.domain.AgentOutput;
import com.aisa.orchestrator.domain.AgentType;
import com.aisa.orchestrator.domain.GenerationRun;
import com.aisa.orchestrator.domain.GenerationRunStatus;
import com.aisa.orchestrator.domain.InvocationStatus;
import com.aisa.orchestrator.kafka.AgentTaskMessage;
import com.aisa.orchestrator.kafka.BlueprintAssemblySignal;
import com.aisa.orchestrator.kafka.KafkaSubmissionService;
import com.aisa.orchestrator.kafka.ProgressEvent;
import com.aisa.orchestrator.repository.AgentInvocationRepository;
import com.aisa.orchestrator.repository.AgentOutputRepository;
import com.aisa.orchestrator.repository.GenerationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates the multi-agent saga for Blueprint generation.
 *
 * <p>This service is responsible for:
 * <ul>
 *   <li>Starting a {@link GenerationRun} and creating initial invocations (Req 6.1)</li>
 *   <li>Using {@link AgentDependencyDag} to determine which agents are ready (Req 6.10)</li>
 *   <li>Publishing work items to the agent-tasks Kafka topic via {@link KafkaSubmissionService}</li>
 *   <li>Passing prerequisite agent outputs as input to dependent agents (Req 6.2)</li>
 *   <li>Handling agent completions: marking invocations complete, persisting outputs</li>
 *   <li>Triggering the next wave of ready agents after each completion</li>
 *   <li>Halting transitive dependents on failure (Req 6.6)</li>
 *   <li>Signalling Blueprint assembly when all agents complete (Req 6.8)</li>
 * </ul>
 *
 * <p>Output persistence ensures safe re-queue: if an orchestrator instance crashes,
 * persisted outputs allow another instance to resume without re-processing (Req 26.6).
 */
@Service
public class SagaExecutionService {

    private static final Logger log = LoggerFactory.getLogger(SagaExecutionService.class);

    /** Maximum total attempts per agent invocation: 1 initial + 3 retries (Req 6.5). */
    static final int MAX_ATTEMPTS = 4;

    private final GenerationRunRepository generationRunRepository;
    private final AgentInvocationRepository agentInvocationRepository;
    private final AgentOutputRepository agentOutputRepository;
    private final AgentDependencyDag dependencyDag;
    private final KafkaSubmissionService kafkaSubmissionService;

    public SagaExecutionService(GenerationRunRepository generationRunRepository,
                                AgentInvocationRepository agentInvocationRepository,
                                AgentOutputRepository agentOutputRepository,
                                AgentDependencyDag dependencyDag,
                                KafkaSubmissionService kafkaSubmissionService) {
        this.generationRunRepository = generationRunRepository;
        this.agentInvocationRepository = agentInvocationRepository;
        this.agentOutputRepository = agentOutputRepository;
        this.dependencyDag = dependencyDag;
        this.kafkaSubmissionService = kafkaSubmissionService;
    }

    /**
     * Starts a new generation run for the given project. Creates the GenerationRun entity,
     * initializes AgentInvocation records for all agents, and dispatches the first wave
     * of ready agents (those with no prerequisites).
     *
     * @param projectId the project to generate a Blueprint for
     * @return the created GenerationRun
     */
    @Transactional
    public GenerationRun startGeneration(UUID projectId) {
        log.info("Starting generation run for project={}", projectId);

        GenerationRun run = new GenerationRun(projectId);
        run.setStatus(GenerationRunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        run = generationRunRepository.save(run);

        // Create an invocation record for each agent in the DAG
        for (AgentType agentType : AgentType.values()) {
            AgentInvocation invocation = new AgentInvocation(run, agentType);
            run.addInvocation(invocation);
        }
        run = generationRunRepository.save(run);

        // Dispatch the first wave: agents with no prerequisites
        dispatchReadyAgents(run);

        return run;
    }

    /**
     * Handles the completion of an agent invocation. Marks the invocation as complete,
     * persists the output, and triggers the next wave of ready agents.
     *
     * <p>On failure, halts all transitive dependents and marks the run as FAILED if
     * no further progress is possible (Req 6.6).
     *
     * @param generationRunId the generation run
     * @param agentType       the agent that completed
     * @param success         whether the agent produced a valid output
     * @param outputContent   the output payload (null if failed)
     * @param completedAt     when the agent completed
     */
    @Transactional
    public void handleAgentCompletion(UUID generationRunId, AgentType agentType,
                                      boolean success, String outputContent, Instant completedAt) {
        GenerationRun run = generationRunRepository.findById(generationRunId)
                .orElseThrow(() -> new IllegalStateException(
                        "GenerationRun not found: " + generationRunId));

        AgentInvocation invocation = findInvocation(run, agentType);

        if (success) {
            handleSuccess(run, invocation, outputContent, completedAt);
        } else {
            handleFailure(run, invocation, completedAt);
        }
    }

    /**
     * Resumes a generation run from its persisted state. Used when an orchestrator
     * instance recovers after a crash — it reads persisted outputs and dispatches
     * any agents that are now ready but haven't been dispatched (Req 26.6).
     *
     * @param generationRunId the run to resume
     */
    @Transactional
    public void resumeGeneration(UUID generationRunId) {
        GenerationRun run = generationRunRepository.findById(generationRunId)
                .orElseThrow(() -> new IllegalStateException(
                        "GenerationRun not found: " + generationRunId));

        if (run.getStatus() != GenerationRunStatus.RUNNING) {
            log.info("Run {} is not in RUNNING state ({}), skipping resume",
                    generationRunId, run.getStatus());
            return;
        }

        log.info("Resuming generation run={}", generationRunId);
        dispatchReadyAgents(run);
    }

    // --- Internal helpers ---

    private void handleSuccess(GenerationRun run, AgentInvocation invocation,
                               String outputContent, Instant completedAt) {
        invocation.setStatus(InvocationStatus.SUCCESS);
        invocation.setCompletedAt(completedAt);

        // Persist output for safe re-queue (Req 6.6, 26.6)
        AgentOutput output = new AgentOutput(invocation, outputContent);
        output.setProducedAt(completedAt);
        invocation.setOutput(output);
        agentInvocationRepository.save(invocation);

        log.info("Agent {} completed successfully for run={} (attempt {})",
                invocation.getAgentType(), run.getId(), invocation.getAttemptCount());

        // Publish SUCCESS progress event (Req 6.7)
        publishProgressEvent(ProgressEvent.success(
                run.getId(), run.getProjectId(), invocation.getAgentType(),
                invocation.getAttemptCount(), MAX_ATTEMPTS));

        // Check if all agents are now complete
        if (isAllComplete(run)) {
            run.setStatus(GenerationRunStatus.COMPLETED);
            run.setCompletedAt(Instant.now());
            generationRunRepository.save(run);
            log.info("All agents completed for run={}. Signalling Blueprint assembly.", run.getId());

            // Publish Blueprint assembly signal (Req 6.8)
            publishAssemblySignal(run);
            return;
        }

        // Dispatch next wave of ready agents
        dispatchReadyAgents(run);
    }

    /**
     * Handles an agent failure with retry logic (Req 6.5).
     *
     * <p>If the agent has remaining attempts ({@code attemptCount < MAX_ATTEMPTS}),
     * the invocation is reset to PENDING and re-dispatched. Otherwise, the invocation
     * is marked FAILED, transitive dependents are halted, and the run is marked FAILED.
     *
     * @param run         the generation run
     * @param invocation  the failed invocation
     * @param completedAt when the failure occurred
     */
    void handleFailure(GenerationRun run, AgentInvocation invocation, Instant completedAt) {
        handleFailure(run, invocation, completedAt, null);
    }

    /**
     * Handles an agent failure with retry logic and an optional error message (Req 6.5).
     */
    void handleFailure(GenerationRun run, AgentInvocation invocation,
                       Instant completedAt, String errorMessage) {
        // Record error message on the invocation (Req 6.9)
        if (errorMessage != null) {
            invocation.setErrorMessage(errorMessage);
        }

        // Retry if attempts remain (Req 6.5: max 4 total attempts)
        if (invocation.getAttemptCount() < MAX_ATTEMPTS) {
            log.info("Agent {} failed on attempt {}/{} for run={}, will retry",
                    invocation.getAgentType(), invocation.getAttemptCount(), MAX_ATTEMPTS, run.getId());

            // Publish RETRY progress event (Req 6.7)
            publishProgressEvent(ProgressEvent.retry(
                    run.getId(), run.getProjectId(), invocation.getAgentType(),
                    invocation.getAttemptCount(), MAX_ATTEMPTS, errorMessage));

            // Reset to PENDING for re-dispatch
            invocation.setStatus(InvocationStatus.PENDING);
            invocation.setCompletedAt(null);
            agentInvocationRepository.save(invocation);

            // Re-dispatch the agent
            Set<AgentType> completedAgents = getCompletedAgents(run);
            dispatchAgent(run, invocation, completedAgents);
            return;
        }

        // All retries exhausted — mark as permanently FAILED
        invocation.setStatus(InvocationStatus.FAILED);
        invocation.setCompletedAt(completedAt);
        agentInvocationRepository.save(invocation);

        log.warn("Agent {} exhausted all {} attempts for run={}",
                invocation.getAgentType(), MAX_ATTEMPTS, run.getId());

        // Publish FAILED progress event (Req 6.7)
        publishProgressEvent(ProgressEvent.failed(
                run.getId(), run.getProjectId(), invocation.getAgentType(),
                invocation.getAttemptCount(), MAX_ATTEMPTS, errorMessage));

        // Halt all transitive dependents while preserving their outputs (Req 6.6)
        haltTransitiveDependents(run, invocation);

        // Mark the run as FAILED
        run.setStatus(GenerationRunStatus.FAILED);
        run.setCompletedAt(Instant.now());
        generationRunRepository.save(run);
    }

    /**
     * Handles an agent timeout. Marks the invocation as TIMED_OUT and triggers
     * the retry/failure flow (Req 6.4).
     *
     * @param run        the generation run
     * @param invocation the timed-out invocation
     */
    void handleTimeout(GenerationRun run, AgentInvocation invocation) {
        log.warn("Agent {} timed out on attempt {}/{} for run={}",
                invocation.getAgentType(), invocation.getAttemptCount(), MAX_ATTEMPTS, run.getId());

        // Publish TIMED_OUT progress event (Req 6.7)
        publishProgressEvent(ProgressEvent.timedOut(
                run.getId(), run.getProjectId(), invocation.getAgentType(),
                invocation.getAttemptCount(), MAX_ATTEMPTS));

        // Timeout counts as a failure — delegate to retry/failure logic
        handleFailure(run, invocation, Instant.now(),
                "Agent did not respond within the timeout period");
    }

    /**
     * Halts all transitive dependents of a failed agent, marking them SKIPPED
     * while preserving any outputs already produced (Req 6.6).
     */
    private void haltTransitiveDependents(GenerationRun run, AgentInvocation failedInvocation) {
        Set<AgentType> transitives = dependencyDag.getTransitiveDependents(failedInvocation.getAgentType());
        for (AgentType dependent : transitives) {
            AgentInvocation depInvocation = findInvocation(run, dependent);
            if (depInvocation.getStatus() == InvocationStatus.PENDING) {
                depInvocation.setStatus(InvocationStatus.SKIPPED);
                agentInvocationRepository.save(depInvocation);

                // Publish SKIPPED progress event (Req 6.7)
                publishProgressEvent(ProgressEvent.skipped(
                        run.getId(), run.getProjectId(), dependent,
                        failedInvocation.getAgentType().name()));

                log.info("Skipped agent {} (transitive dependent of failed {})",
                        dependent, failedInvocation.getAgentType());
            }
        }
    }

    /**
     * Determines which agents are ready to execute (all prerequisites completed)
     * and dispatches them to Kafka concurrently (Req 6.10).
     */
    void dispatchReadyAgents(GenerationRun run) {
        Set<AgentType> completedAgents = getCompletedAgents(run);
        Set<AgentType> runningAgents = getRunningAgents(run);
        Set<AgentType> failedOrSkipped = getFailedOrSkippedAgents(run);

        Set<AgentType> readyAgents = dependencyDag.getReadyAgents(completedAgents);

        // Filter out agents that are already running, failed, or skipped
        for (AgentType agentType : readyAgents) {
            if (runningAgents.contains(agentType) || failedOrSkipped.contains(agentType)) {
                continue;
            }

            AgentInvocation invocation = findInvocation(run, agentType);
            if (invocation.getStatus() != InvocationStatus.PENDING) {
                continue;
            }

            dispatchAgent(run, invocation, completedAgents);
        }
    }

    private void dispatchAgent(GenerationRun run, AgentInvocation invocation,
                               Set<AgentType> completedAgents) {
        AgentType agentType = invocation.getAgentType();

        // Gather prerequisite outputs (Req 6.2)
        Map<AgentType, String> prerequisiteOutputs = gatherPrerequisiteOutputs(run, agentType);

        // Mark as running
        invocation.setStatus(InvocationStatus.RUNNING);
        invocation.setStartedAt(Instant.now());
        invocation.incrementAttemptCount();
        agentInvocationRepository.save(invocation);

        // Build task message
        AgentTaskMessage message = new AgentTaskMessage(
                run.getId(),
                invocation.getId(),
                run.getProjectId(),
                agentType,
                prerequisiteOutputs
        );

        // Publish to agent-tasks topic
        String key = run.getId().toString();
        kafkaSubmissionService.submit(KafkaTopics.AGENT_TASKS, key, message);

        // Publish STARTED progress event (Req 6.7)
        publishProgressEvent(ProgressEvent.started(
                run.getId(), run.getProjectId(), agentType,
                invocation.getAttemptCount(), MAX_ATTEMPTS));

        log.info("Dispatched agent {} for run={} (attempt {})",
                agentType, run.getId(), invocation.getAttemptCount());
    }

    /**
     * Collects the persisted outputs of all prerequisite agents for the given agent type.
     * These are passed as input to the dependent agent (Req 6.2).
     */
    Map<AgentType, String> gatherPrerequisiteOutputs(GenerationRun run, AgentType agentType) {
        Set<AgentType> prerequisites = dependencyDag.getDependencies(agentType);
        Map<AgentType, String> outputs = new HashMap<>();

        for (AgentType prereq : prerequisites) {
            AgentInvocation prereqInvocation = findInvocation(run, prereq);
            AgentOutput prereqOutput = prereqInvocation.getOutput();
            if (prereqOutput != null) {
                outputs.put(prereq, prereqOutput.getContent());
            }
        }

        return outputs;
    }

    private Set<AgentType> getCompletedAgents(GenerationRun run) {
        EnumSet<AgentType> completed = EnumSet.noneOf(AgentType.class);
        for (AgentInvocation inv : run.getInvocations()) {
            if (inv.getStatus() == InvocationStatus.SUCCESS) {
                completed.add(inv.getAgentType());
            }
        }
        return completed;
    }

    private Set<AgentType> getRunningAgents(GenerationRun run) {
        EnumSet<AgentType> running = EnumSet.noneOf(AgentType.class);
        for (AgentInvocation inv : run.getInvocations()) {
            if (inv.getStatus() == InvocationStatus.RUNNING) {
                running.add(inv.getAgentType());
            }
        }
        return running;
    }

    private Set<AgentType> getFailedOrSkippedAgents(GenerationRun run) {
        EnumSet<AgentType> result = EnumSet.noneOf(AgentType.class);
        for (AgentInvocation inv : run.getInvocations()) {
            if (inv.getStatus() == InvocationStatus.FAILED
                    || inv.getStatus() == InvocationStatus.SKIPPED) {
                result.add(inv.getAgentType());
            }
        }
        return result;
    }

    private boolean isAllComplete(GenerationRun run) {
        for (AgentInvocation inv : run.getInvocations()) {
            if (inv.getStatus() != InvocationStatus.SUCCESS) {
                return false;
            }
        }
        return true;
    }

    AgentInvocation findInvocation(GenerationRun run, AgentType agentType) {
        return run.getInvocations().stream()
                .filter(inv -> inv.getAgentType() == agentType)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No invocation found for agent " + agentType + " in run " + run.getId()));
    }

    // --- Progress event and assembly signal publishing ---

    /**
     * Publishes a progress event to the agent-progress Kafka topic.
     * Failures are logged but do not halt saga progression (best-effort).
     */
    private void publishProgressEvent(ProgressEvent event) {
        try {
            kafkaSubmissionService.submit(
                    KafkaTopics.AGENT_PROGRESS,
                    event.generationRunId().toString(),
                    event);
            log.debug("Published progress event: run={} agent={} type={}",
                    event.generationRunId(), event.agentType(), event.eventType());
        } catch (Exception e) {
            // Best-effort: progress events are informational. Don't fail the saga.
            log.warn("Failed to publish progress event for run={} agent={}: {}",
                    event.generationRunId(), event.agentType(), e.getMessage());
        }
    }

    /**
     * Publishes a Blueprint assembly signal when all agents complete (Req 6.8).
     */
    private void publishAssemblySignal(GenerationRun run) {
        try {
            BlueprintAssemblySignal signal = BlueprintAssemblySignal.of(
                    run.getId(), run.getProjectId());
            kafkaSubmissionService.submit(
                    KafkaTopics.PROJECT_STATE_CHANGES,
                    run.getProjectId().toString(),
                    signal);
            log.info("Published Blueprint assembly signal for run={} project={}",
                    run.getId(), run.getProjectId());
        } catch (Exception e) {
            log.error("Failed to publish assembly signal for run={}: {}",
                    run.getId(), e.getMessage(), e);
        }
    }
}
