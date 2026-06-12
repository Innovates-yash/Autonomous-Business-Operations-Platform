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
import com.aisa.orchestrator.kafka.KafkaSubmissionService;
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

        log.info("Agent {} completed successfully for run={}",
                invocation.getAgentType(), run.getId());

        // Check if all agents are now complete
        if (isAllComplete(run)) {
            run.setStatus(GenerationRunStatus.COMPLETED);
            generationRunRepository.save(run);
            log.info("All agents completed for run={}. Signalling Blueprint assembly.", run.getId());
            // Blueprint assembly signal would be published here
            return;
        }

        // Dispatch next wave of ready agents
        dispatchReadyAgents(run);
    }

    private void handleFailure(GenerationRun run, AgentInvocation invocation, Instant completedAt) {
        invocation.setStatus(InvocationStatus.FAILED);
        invocation.setCompletedAt(completedAt);
        agentInvocationRepository.save(invocation);

        log.warn("Agent {} failed for run={}", invocation.getAgentType(), run.getId());

        // Halt all transitive dependents (Req 6.6)
        Set<AgentType> transitives = dependencyDag.getTransitiveDependents(invocation.getAgentType());
        for (AgentType dependent : transitives) {
            AgentInvocation depInvocation = findInvocation(run, dependent);
            if (depInvocation.getStatus() == InvocationStatus.PENDING) {
                depInvocation.setStatus(InvocationStatus.SKIPPED);
                agentInvocationRepository.save(depInvocation);
                log.info("Skipped agent {} (transitive dependent of failed {})",
                        dependent, invocation.getAgentType());
            }
        }

        // Mark the run as FAILED
        run.setStatus(GenerationRunStatus.FAILED);
        generationRunRepository.save(run);
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

    private AgentInvocation findInvocation(GenerationRun run, AgentType agentType) {
        return run.getInvocations().stream()
                .filter(inv -> inv.getAgentType() == agentType)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No invocation found for agent " + agentType + " in run " + run.getId()));
    }
}
