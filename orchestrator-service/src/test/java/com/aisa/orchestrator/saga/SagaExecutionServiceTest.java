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
import com.aisa.orchestrator.kafka.SubmissionResult;
import com.aisa.orchestrator.repository.AgentInvocationRepository;
import com.aisa.orchestrator.repository.AgentOutputRepository;
import com.aisa.orchestrator.repository.GenerationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SagaExecutionService}.
 * Validates: Requirements 6.1, 6.2, 6.10.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>DAG evaluation dispatches correct agents (those with all prerequisites satisfied)</li>
 *   <li>Concurrent independent agents are dispatched together</li>
 *   <li>Prerequisite outputs are passed to dependent agents</li>
 *   <li>Persist-and-resume: after crash, persisted outputs allow resumption</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SagaExecutionServiceTest {

    @Mock
    private GenerationRunRepository generationRunRepository;

    @Mock
    private AgentInvocationRepository agentInvocationRepository;

    @Mock
    private AgentOutputRepository agentOutputRepository;

    @Mock
    private KafkaSubmissionService kafkaSubmissionService;

    @Captor
    private ArgumentCaptor<Object> payloadCaptor;

    private AgentDependencyDag dependencyDag;
    private SagaExecutionService sagaExecutionService;

    @BeforeEach
    void setUp() {
        dependencyDag = new AgentDependencyDag();
        sagaExecutionService = new SagaExecutionService(
                generationRunRepository,
                agentInvocationRepository,
                agentOutputRepository,
                dependencyDag,
                kafkaSubmissionService
        );
    }

    // --- Helpers ---

    private GenerationRun createRunWithAllInvocations(UUID projectId) {
        GenerationRun run = new GenerationRun(projectId);
        // Simulate JPA-assigned id
        setField(run, "id", UUID.randomUUID());
        run.setStatus(GenerationRunStatus.RUNNING);
        for (AgentType agentType : AgentType.values()) {
            AgentInvocation invocation = new AgentInvocation(run, agentType);
            setField(invocation, "id", UUID.randomUUID());
            run.addInvocation(invocation);
        }
        return run;
    }

    private AgentInvocation findInvocation(GenerationRun run, AgentType agentType) {
        return run.getInvocations().stream()
                .filter(inv -> inv.getAgentType() == agentType)
                .findFirst()
                .orElseThrow();
    }

    private void markSuccess(GenerationRun run, AgentType agentType, String outputContent) {
        AgentInvocation invocation = findInvocation(run, agentType);
        invocation.setStatus(InvocationStatus.SUCCESS);
        invocation.setStartedAt(Instant.now().minusSeconds(10));
        invocation.setCompletedAt(Instant.now());
        AgentOutput output = new AgentOutput(invocation, outputContent);
        invocation.setOutput(output);
    }

    @SuppressWarnings("unchecked")
    private static void setField(Object obj, String fieldName, Object value) {
        try {
            var field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }

    private SubmissionResult ackResult() {
        return new SubmissionResult(SubmissionResult.Status.ACKNOWLEDGED, "agent-tasks", 0, 1L);
    }

    // ===================================================================
    // Tests
    // ===================================================================

    @Nested
    @DisplayName("startGeneration — DAG evaluation dispatches correct agents")
    class StartGeneration {

        @Test
        @DisplayName("dispatches only REQUIREMENT_ANALYST on start (only agent with no prerequisites)")
        void dispatches_onlyRootAgent_onStart() {
            // Arrange
            UUID projectId = UUID.randomUUID();
            when(generationRunRepository.save(any(GenerationRun.class)))
                    .thenAnswer(invocation -> {
                        GenerationRun run = invocation.getArgument(0);
                        if (run.getId() == null) {
                            setField(run, "id", UUID.randomUUID());
                        }
                        // Simulate JPA cascading ids for invocations
                        for (AgentInvocation inv : run.getInvocations()) {
                            if (inv.getId() == null) {
                                setField(inv, "id", UUID.randomUUID());
                            }
                        }
                        return run;
                    });
            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(kafkaSubmissionService.submit(any(), any(), any())).thenReturn(ackResult());

            // Act
            GenerationRun result = sagaExecutionService.startGeneration(projectId);

            // Assert
            assertThat(result.getStatus()).isEqualTo(GenerationRunStatus.RUNNING);
            assertThat(result.getProjectId()).isEqualTo(projectId);
            assertThat(result.getInvocations()).hasSize(AgentType.values().length);

            // Verify only REQUIREMENT_ANALYST is dispatched (the only root agent)
            verify(kafkaSubmissionService, times(1)).submit(
                    eq(KafkaTopics.AGENT_TASKS), any(), payloadCaptor.capture());

            AgentTaskMessage message = (AgentTaskMessage) payloadCaptor.getValue();
            assertThat(message.agentType()).isEqualTo(AgentType.REQUIREMENT_ANALYST);
            assertThat(message.prerequisiteOutputs()).isEmpty();
        }

        @Test
        @DisplayName("creates invocations for all 10 agents in the DAG")
        void creates_invocationsForAllAgents() {
            // Arrange
            UUID projectId = UUID.randomUUID();
            when(generationRunRepository.save(any(GenerationRun.class)))
                    .thenAnswer(invocation -> {
                        GenerationRun run = invocation.getArgument(0);
                        if (run.getId() == null) {
                            setField(run, "id", UUID.randomUUID());
                        }
                        for (AgentInvocation inv : run.getInvocations()) {
                            if (inv.getId() == null) {
                                setField(inv, "id", UUID.randomUUID());
                            }
                        }
                        return run;
                    });
            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(kafkaSubmissionService.submit(any(), any(), any())).thenReturn(ackResult());

            // Act
            GenerationRun result = sagaExecutionService.startGeneration(projectId);

            // Assert — one invocation per agent type
            assertThat(result.getInvocations())
                    .extracting(AgentInvocation::getAgentType)
                    .containsExactlyInAnyOrder(AgentType.values());
        }
    }

    @Nested
    @DisplayName("Concurrent independent agents (Req 6.10)")
    class ConcurrentDispatching {

        @Test
        @DisplayName("dispatches DATABASE_ARCHITECT, SECURITY_ARCHITECT, API_ARCHITECT concurrently after SOFTWARE_ARCHITECT completes")
        void dispatches_threeIndependentAgents_concurrently() {
            // Arrange — simulate run where REQUIREMENT_ANALYST -> BUSINESS_ANALYST ->
            // PRODUCT_MANAGER -> SOFTWARE_ARCHITECT are all complete
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);

            markSuccess(run, AgentType.REQUIREMENT_ANALYST, "{\"requirements\": []}");
            markSuccess(run, AgentType.BUSINESS_ANALYST, "{\"stakeholders\": []}");
            markSuccess(run, AgentType.PRODUCT_MANAGER, "{\"stories\": []}");
            markSuccess(run, AgentType.SOFTWARE_ARCHITECT, "{\"architecture\": {}}");

            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(kafkaSubmissionService.submit(any(), any(), any())).thenReturn(ackResult());

            // Act — dispatch ready agents
            sagaExecutionService.dispatchReadyAgents(run);

            // Assert — three agents should be dispatched concurrently
            verify(kafkaSubmissionService, times(3)).submit(
                    eq(KafkaTopics.AGENT_TASKS), any(), payloadCaptor.capture());

            List<AgentType> dispatchedAgents = payloadCaptor.getAllValues().stream()
                    .map(payload -> ((AgentTaskMessage) payload).agentType())
                    .toList();

            assertThat(dispatchedAgents).containsExactlyInAnyOrder(
                    AgentType.DATABASE_ARCHITECT,
                    AgentType.SECURITY_ARCHITECT,
                    AgentType.API_ARCHITECT
            );
        }

        @Test
        @DisplayName("dispatches DEVOPS_ARCHITECT and COST_ESTIMATION concurrently after DB/Security/API complete")
        void dispatches_devopsAndCost_concurrently() {
            // Arrange — all predecessors of DEVOPS and COST complete
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);

            markSuccess(run, AgentType.REQUIREMENT_ANALYST, "{\"requirements\": []}");
            markSuccess(run, AgentType.BUSINESS_ANALYST, "{\"stakeholders\": []}");
            markSuccess(run, AgentType.PRODUCT_MANAGER, "{\"stories\": []}");
            markSuccess(run, AgentType.SOFTWARE_ARCHITECT, "{\"architecture\": {}}");
            markSuccess(run, AgentType.DATABASE_ARCHITECT, "{\"er_design\": {}}");
            markSuccess(run, AgentType.SECURITY_ARCHITECT, "{\"security\": {}}");
            markSuccess(run, AgentType.API_ARCHITECT, "{\"api_design\": {}}");

            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(kafkaSubmissionService.submit(any(), any(), any())).thenReturn(ackResult());

            // Act
            sagaExecutionService.dispatchReadyAgents(run);

            // Assert — two agents dispatched concurrently
            verify(kafkaSubmissionService, times(2)).submit(
                    eq(KafkaTopics.AGENT_TASKS), any(), payloadCaptor.capture());

            List<AgentType> dispatchedAgents = payloadCaptor.getAllValues().stream()
                    .map(payload -> ((AgentTaskMessage) payload).agentType())
                    .toList();

            assertThat(dispatchedAgents).containsExactlyInAnyOrder(
                    AgentType.DEVOPS_ARCHITECT,
                    AgentType.COST_ESTIMATION
            );
        }

        @Test
        @DisplayName("does not dispatch agent whose prerequisites are not all complete")
        void doesNotDispatch_agentWithIncompletePrereqs() {
            // Arrange — only REQUIREMENT_ANALYST complete, BUSINESS_ANALYST should be ready
            // but SOFTWARE_ARCHITECT should NOT be
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);

            markSuccess(run, AgentType.REQUIREMENT_ANALYST, "{\"requirements\": []}");

            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(kafkaSubmissionService.submit(any(), any(), any())).thenReturn(ackResult());

            // Act
            sagaExecutionService.dispatchReadyAgents(run);

            // Assert — only BUSINESS_ANALYST dispatched
            verify(kafkaSubmissionService, times(1)).submit(
                    eq(KafkaTopics.AGENT_TASKS), any(), payloadCaptor.capture());

            AgentTaskMessage message = (AgentTaskMessage) payloadCaptor.getValue();
            assertThat(message.agentType()).isEqualTo(AgentType.BUSINESS_ANALYST);
        }
    }

    @Nested
    @DisplayName("Prerequisite output passing (Req 6.2)")
    class PrerequisiteOutputPassing {

        @Test
        @DisplayName("passes REQUIREMENT_ANALYST output to BUSINESS_ANALYST")
        void passes_prerequisiteOutput_toDependent() {
            // Arrange
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);

            String reqAnalystOutput = "{\"functional\": [\"FR-1\"], \"non_functional\": [\"NFR-1\"]}";
            markSuccess(run, AgentType.REQUIREMENT_ANALYST, reqAnalystOutput);

            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(kafkaSubmissionService.submit(any(), any(), any())).thenReturn(ackResult());

            // Act
            sagaExecutionService.dispatchReadyAgents(run);

            // Assert — BUSINESS_ANALYST receives REQUIREMENT_ANALYST output
            verify(kafkaSubmissionService).submit(eq(KafkaTopics.AGENT_TASKS), any(), payloadCaptor.capture());

            AgentTaskMessage message = (AgentTaskMessage) payloadCaptor.getValue();
            assertThat(message.agentType()).isEqualTo(AgentType.BUSINESS_ANALYST);
            assertThat(message.prerequisiteOutputs())
                    .containsEntry(AgentType.REQUIREMENT_ANALYST, reqAnalystOutput);
        }

        @Test
        @DisplayName("passes multiple prerequisite outputs to DEVOPS_ARCHITECT (DB, Security, API)")
        void passes_multiplePrerequisiteOutputs() {
            // Arrange
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);

            markSuccess(run, AgentType.REQUIREMENT_ANALYST, "{\"reqs\": []}");
            markSuccess(run, AgentType.BUSINESS_ANALYST, "{\"biz\": []}");
            markSuccess(run, AgentType.PRODUCT_MANAGER, "{\"stories\": []}");
            markSuccess(run, AgentType.SOFTWARE_ARCHITECT, "{\"arch\": {}}");

            String dbOutput = "{\"er_design\": {\"entities\": [\"User\", \"Project\"]}}";
            String secOutput = "{\"security\": {\"auth\": \"JWT\"}}";
            String apiOutput = "{\"api\": {\"endpoints\": [\"/users\"]}}";
            markSuccess(run, AgentType.DATABASE_ARCHITECT, dbOutput);
            markSuccess(run, AgentType.SECURITY_ARCHITECT, secOutput);
            markSuccess(run, AgentType.API_ARCHITECT, apiOutput);

            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(kafkaSubmissionService.submit(any(), any(), any())).thenReturn(ackResult());

            // Act
            sagaExecutionService.dispatchReadyAgents(run);

            // Assert — DEVOPS_ARCHITECT receives all three prerequisite outputs
            verify(kafkaSubmissionService, times(2)).submit(
                    eq(KafkaTopics.AGENT_TASKS), any(), payloadCaptor.capture());

            // Find DEVOPS_ARCHITECT message
            AgentTaskMessage devopsMessage = payloadCaptor.getAllValues().stream()
                    .map(p -> (AgentTaskMessage) p)
                    .filter(m -> m.agentType() == AgentType.DEVOPS_ARCHITECT)
                    .findFirst()
                    .orElseThrow();

            assertThat(devopsMessage.prerequisiteOutputs())
                    .hasSize(3)
                    .containsEntry(AgentType.DATABASE_ARCHITECT, dbOutput)
                    .containsEntry(AgentType.SECURITY_ARCHITECT, secOutput)
                    .containsEntry(AgentType.API_ARCHITECT, apiOutput);
        }

        @Test
        @DisplayName("REQUIREMENT_ANALYST is dispatched with empty prerequisite outputs (root agent)")
        void rootAgent_receivesEmptyPrerequisites() {
            // Arrange
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);

            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(kafkaSubmissionService.submit(any(), any(), any())).thenReturn(ackResult());

            // Act
            sagaExecutionService.dispatchReadyAgents(run);

            // Assert
            verify(kafkaSubmissionService).submit(eq(KafkaTopics.AGENT_TASKS), any(), payloadCaptor.capture());
            AgentTaskMessage message = (AgentTaskMessage) payloadCaptor.getValue();
            assertThat(message.agentType()).isEqualTo(AgentType.REQUIREMENT_ANALYST);
            assertThat(message.prerequisiteOutputs()).isEmpty();
        }
    }

    @Nested
    @DisplayName("handleAgentCompletion — persist outputs and re-evaluate DAG")
    class HandleCompletion {

        @Test
        @DisplayName("on success: persists AgentOutput and dispatches next ready agent")
        void onSuccess_persistsOutput_andDispatchesNext() {
            // Arrange
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);
            run.setStatus(GenerationRunStatus.RUNNING);

            // REQUIREMENT_ANALYST is RUNNING
            AgentInvocation reqInvocation = findInvocation(run, AgentType.REQUIREMENT_ANALYST);
            reqInvocation.setStatus(InvocationStatus.RUNNING);
            reqInvocation.setStartedAt(Instant.now().minusSeconds(5));

            when(generationRunRepository.findById(run.getId())).thenReturn(Optional.of(run));
            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(kafkaSubmissionService.submit(any(), any(), any())).thenReturn(ackResult());

            String outputContent = "{\"functional\": [\"FR-1\"]}";
            Instant completedAt = Instant.now();

            // Act
            sagaExecutionService.handleAgentCompletion(
                    run.getId(), AgentType.REQUIREMENT_ANALYST, true, outputContent, completedAt);

            // Assert — invocation marked SUCCESS with output persisted
            assertThat(reqInvocation.getStatus()).isEqualTo(InvocationStatus.SUCCESS);
            assertThat(reqInvocation.getOutput()).isNotNull();
            assertThat(reqInvocation.getOutput().getContent()).isEqualTo(outputContent);

            // Next agent (BUSINESS_ANALYST) is dispatched
            verify(kafkaSubmissionService).submit(eq(KafkaTopics.AGENT_TASKS), any(), payloadCaptor.capture());
            AgentTaskMessage nextMessage = (AgentTaskMessage) payloadCaptor.getValue();
            assertThat(nextMessage.agentType()).isEqualTo(AgentType.BUSINESS_ANALYST);
        }

        @Test
        @DisplayName("on failure with retries exhausted: marks run FAILED and skips transitive dependents")
        void onFailure_exhausted_haltsTransitiveDependents() {
            // Arrange
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);
            run.setStatus(GenerationRunStatus.RUNNING);

            AgentInvocation reqInvocation = findInvocation(run, AgentType.REQUIREMENT_ANALYST);
            reqInvocation.setStatus(InvocationStatus.RUNNING);
            // Set attempt count to MAX_ATTEMPTS so retries are exhausted
            setField(reqInvocation, "attemptCount", SagaExecutionService.MAX_ATTEMPTS);

            when(generationRunRepository.findById(run.getId())).thenReturn(Optional.of(run));
            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(generationRunRepository.save(any(GenerationRun.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(kafkaSubmissionService.submit(any(), any(), any())).thenReturn(ackResult());

            // Act
            sagaExecutionService.handleAgentCompletion(
                    run.getId(), AgentType.REQUIREMENT_ANALYST, false, null, Instant.now());

            // Assert — run marked FAILED
            assertThat(run.getStatus()).isEqualTo(GenerationRunStatus.FAILED);
            assertThat(reqInvocation.getStatus()).isEqualTo(InvocationStatus.FAILED);

            // All transitive dependents (everything after REQUIREMENT_ANALYST) are SKIPPED
            for (AgentType dependent : dependencyDag.getTransitiveDependents(AgentType.REQUIREMENT_ANALYST)) {
                AgentInvocation depInv = findInvocation(run, dependent);
                assertThat(depInv.getStatus()).isEqualTo(InvocationStatus.SKIPPED);
            }
        }

        @Test
        @DisplayName("on all agents complete: marks run COMPLETED (Req 6.8)")
        void onAllComplete_marksRunCompleted() {
            // Arrange — all agents already SUCCESS except DOCUMENTATION (final)
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);
            run.setStatus(GenerationRunStatus.RUNNING);

            // Mark all agents except DOCUMENTATION as complete
            for (AgentType agentType : AgentType.values()) {
                if (agentType != AgentType.DOCUMENTATION) {
                    markSuccess(run, agentType, "{\"output\": \"" + agentType + "\"}");
                }
            }

            // DOCUMENTATION is currently RUNNING
            AgentInvocation docInvocation = findInvocation(run, AgentType.DOCUMENTATION);
            docInvocation.setStatus(InvocationStatus.RUNNING);
            docInvocation.setStartedAt(Instant.now().minusSeconds(20));

            when(generationRunRepository.findById(run.getId())).thenReturn(Optional.of(run));
            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(generationRunRepository.save(any(GenerationRun.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            sagaExecutionService.handleAgentCompletion(
                    run.getId(), AgentType.DOCUMENTATION, true, "{\"doc\": \"complete\"}",
                    Instant.now());

            // Assert — run is COMPLETED
            assertThat(run.getStatus()).isEqualTo(GenerationRunStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("Persist-and-resume (Req 26.6)")
    class PersistAndResume {

        @Test
        @DisplayName("resumeGeneration dispatches ready agents based on persisted outputs")
        void resume_dispatchesReadyAgents_fromPersistedState() {
            // Arrange — simulate a crashed instance that had REQUIREMENT_ANALYST complete
            // but never dispatched BUSINESS_ANALYST
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);
            run.setStatus(GenerationRunStatus.RUNNING);

            // REQUIREMENT_ANALYST completed (persisted output)
            markSuccess(run, AgentType.REQUIREMENT_ANALYST, "{\"requirements\": [\"FR-1\"]}");

            // BUSINESS_ANALYST is still PENDING (never dispatched before crash)
            AgentInvocation bizInvocation = findInvocation(run, AgentType.BUSINESS_ANALYST);
            assertThat(bizInvocation.getStatus()).isEqualTo(InvocationStatus.PENDING);

            when(generationRunRepository.findById(run.getId())).thenReturn(Optional.of(run));
            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(kafkaSubmissionService.submit(any(), any(), any())).thenReturn(ackResult());

            // Act
            sagaExecutionService.resumeGeneration(run.getId());

            // Assert — BUSINESS_ANALYST is dispatched
            verify(kafkaSubmissionService).submit(eq(KafkaTopics.AGENT_TASKS), any(), payloadCaptor.capture());
            AgentTaskMessage message = (AgentTaskMessage) payloadCaptor.getValue();
            assertThat(message.agentType()).isEqualTo(AgentType.BUSINESS_ANALYST);
            assertThat(message.prerequisiteOutputs())
                    .containsKey(AgentType.REQUIREMENT_ANALYST);
        }

        @Test
        @DisplayName("resumeGeneration dispatches multiple ready agents after crash mid-parallel")
        void resume_dispatchesMultipleReady_afterCrashMidParallel() {
            // Arrange — crashed after SOFTWARE_ARCHITECT complete, DB/Security/API never dispatched
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);
            run.setStatus(GenerationRunStatus.RUNNING);

            markSuccess(run, AgentType.REQUIREMENT_ANALYST, "{\"r\": []}");
            markSuccess(run, AgentType.BUSINESS_ANALYST, "{\"b\": []}");
            markSuccess(run, AgentType.PRODUCT_MANAGER, "{\"p\": []}");
            markSuccess(run, AgentType.SOFTWARE_ARCHITECT, "{\"arch\": {}}");

            when(generationRunRepository.findById(run.getId())).thenReturn(Optional.of(run));
            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(kafkaSubmissionService.submit(any(), any(), any())).thenReturn(ackResult());

            // Act
            sagaExecutionService.resumeGeneration(run.getId());

            // Assert — 3 agents dispatched concurrently
            verify(kafkaSubmissionService, times(3)).submit(
                    eq(KafkaTopics.AGENT_TASKS), any(), payloadCaptor.capture());

            List<AgentType> dispatched = payloadCaptor.getAllValues().stream()
                    .map(p -> ((AgentTaskMessage) p).agentType())
                    .toList();

            assertThat(dispatched).containsExactlyInAnyOrder(
                    AgentType.DATABASE_ARCHITECT,
                    AgentType.SECURITY_ARCHITECT,
                    AgentType.API_ARCHITECT
            );
        }

        @Test
        @DisplayName("resumeGeneration does nothing for a non-RUNNING run")
        void resume_skips_nonRunningRun() {
            // Arrange
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);
            run.setStatus(GenerationRunStatus.COMPLETED);

            when(generationRunRepository.findById(run.getId())).thenReturn(Optional.of(run));

            // Act
            sagaExecutionService.resumeGeneration(run.getId());

            // Assert — no dispatch
            verify(kafkaSubmissionService, never()).submit(any(), any(), any());
        }

        @Test
        @DisplayName("resumeGeneration does not re-dispatch agents already RUNNING")
        void resume_doesNotRedispatch_runningAgents() {
            // Arrange — REQUIREMENT_ANALYST complete, BUSINESS_ANALYST already running
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);
            run.setStatus(GenerationRunStatus.RUNNING);

            markSuccess(run, AgentType.REQUIREMENT_ANALYST, "{\"reqs\": []}");

            AgentInvocation bizInvocation = findInvocation(run, AgentType.BUSINESS_ANALYST);
            bizInvocation.setStatus(InvocationStatus.RUNNING);
            bizInvocation.setStartedAt(Instant.now().minusSeconds(30));

            when(generationRunRepository.findById(run.getId())).thenReturn(Optional.of(run));

            // Act
            sagaExecutionService.resumeGeneration(run.getId());

            // Assert — no dispatch (BUSINESS_ANALYST already running)
            verify(kafkaSubmissionService, never()).submit(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Retry logic (Req 6.5)")
    class RetryLogic {

        @Test
        @DisplayName("failure at attempt 1 triggers retry (re-dispatch)")
        void failureAtAttempt1_triggersRetry() {
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);
            run.setStatus(GenerationRunStatus.RUNNING);

            AgentInvocation reqInvocation = findInvocation(run, AgentType.REQUIREMENT_ANALYST);
            reqInvocation.setStatus(InvocationStatus.RUNNING);
            reqInvocation.setStartedAt(Instant.now().minusSeconds(5));
            setField(reqInvocation, "attemptCount", 1); // first attempt

            when(generationRunRepository.findById(run.getId())).thenReturn(Optional.of(run));
            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(kafkaSubmissionService.submit(any(), any(), any())).thenReturn(ackResult());

            // Act
            sagaExecutionService.handleAgentCompletion(
                    run.getId(), AgentType.REQUIREMENT_ANALYST, false, null, Instant.now());

            // Assert — NOT FAILED, agent re-dispatched (RUNNING after re-dispatch)
            assertThat(reqInvocation.getStatus()).isEqualTo(InvocationStatus.RUNNING);
            assertThat(reqInvocation.getAttemptCount()).isEqualTo(2); // incremented from 1 to 2
            assertThat(run.getStatus()).isEqualTo(GenerationRunStatus.RUNNING); // run NOT failed

            // Verify agent-tasks dispatch (retry)
            verify(kafkaSubmissionService).submit(
                    eq(KafkaTopics.AGENT_TASKS), any(), any());
        }

        @Test
        @DisplayName("failure at attempt 4 (max) marks permanently FAILED")
        void failureAtMaxAttempts_marksFailed() {
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);
            run.setStatus(GenerationRunStatus.RUNNING);

            AgentInvocation reqInvocation = findInvocation(run, AgentType.REQUIREMENT_ANALYST);
            reqInvocation.setStatus(InvocationStatus.RUNNING);
            setField(reqInvocation, "attemptCount", SagaExecutionService.MAX_ATTEMPTS);

            when(generationRunRepository.findById(run.getId())).thenReturn(Optional.of(run));
            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(generationRunRepository.save(any(GenerationRun.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(kafkaSubmissionService.submit(any(), any(), any())).thenReturn(ackResult());

            // Act
            sagaExecutionService.handleAgentCompletion(
                    run.getId(), AgentType.REQUIREMENT_ANALYST, false, null, Instant.now());

            // Assert
            assertThat(reqInvocation.getStatus()).isEqualTo(InvocationStatus.FAILED);
            assertThat(run.getStatus()).isEqualTo(GenerationRunStatus.FAILED);
        }

        @Test
        @DisplayName("failure at attempt 3 triggers final retry (attempt count becomes 4)")
        void failureAtAttempt3_triggersLastRetry() {
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);
            run.setStatus(GenerationRunStatus.RUNNING);

            AgentInvocation reqInvocation = findInvocation(run, AgentType.REQUIREMENT_ANALYST);
            reqInvocation.setStatus(InvocationStatus.RUNNING);
            setField(reqInvocation, "attemptCount", 3); // third attempt

            when(generationRunRepository.findById(run.getId())).thenReturn(Optional.of(run));
            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(kafkaSubmissionService.submit(any(), any(), any())).thenReturn(ackResult());

            // Act
            sagaExecutionService.handleAgentCompletion(
                    run.getId(), AgentType.REQUIREMENT_ANALYST, false, null, Instant.now());

            // Assert — retried (attempt 4)
            assertThat(reqInvocation.getStatus()).isEqualTo(InvocationStatus.RUNNING);
            assertThat(reqInvocation.getAttemptCount()).isEqualTo(4);
            assertThat(run.getStatus()).isEqualTo(GenerationRunStatus.RUNNING);
        }
    }

    @Nested
    @DisplayName("Progress events and assembly signal (Req 6.7, 6.8)")
    class ProgressAndAssembly {

        @Test
        @DisplayName("on success: publishes SUCCESS progress event to agent-progress topic")
        void onSuccess_publishesProgressEvent() {
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);
            run.setStatus(GenerationRunStatus.RUNNING);

            AgentInvocation reqInvocation = findInvocation(run, AgentType.REQUIREMENT_ANALYST);
            reqInvocation.setStatus(InvocationStatus.RUNNING);
            reqInvocation.setStartedAt(Instant.now().minusSeconds(5));
            setField(reqInvocation, "attemptCount", 1);

            when(generationRunRepository.findById(run.getId())).thenReturn(Optional.of(run));
            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(kafkaSubmissionService.submit(any(), any(), any())).thenReturn(ackResult());

            sagaExecutionService.handleAgentCompletion(
                    run.getId(), AgentType.REQUIREMENT_ANALYST, true,
                    "{\"output\": \"test\"}", Instant.now());

            // Verify progress events published to agent-progress topic
            // (SUCCESS for completed agent + STARTED for next dispatched agent)
            verify(kafkaSubmissionService, times(2)).submit(
                    eq(KafkaTopics.AGENT_PROGRESS), any(), payloadCaptor.capture());

            // Find the SUCCESS progress event (filter out STARTED events from dispatch)
            List<Object> allPayloads = payloadCaptor.getAllValues();
            ProgressEvent successEvent = allPayloads.stream()
                    .filter(p -> p instanceof ProgressEvent)
                    .map(p -> (ProgressEvent) p)
                    .filter(e -> e.eventType() == ProgressEvent.EventType.SUCCESS)
                    .findFirst()
                    .orElse(null);

            assertThat(successEvent).isNotNull();
            assertThat(successEvent.agentType()).isEqualTo(AgentType.REQUIREMENT_ANALYST);
            assertThat(successEvent.generationRunId()).isEqualTo(run.getId());
        }

        @Test
        @DisplayName("on all agents complete: publishes assembly signal")
        void onAllComplete_publishesAssemblySignal() {
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);
            run.setStatus(GenerationRunStatus.RUNNING);

            // Mark all agents except DOCUMENTATION as complete
            for (AgentType agentType : AgentType.values()) {
                if (agentType != AgentType.DOCUMENTATION) {
                    markSuccess(run, agentType, "{\"output\": \"" + agentType + "\"}");
                }
            }

            AgentInvocation docInvocation = findInvocation(run, AgentType.DOCUMENTATION);
            docInvocation.setStatus(InvocationStatus.RUNNING);
            docInvocation.setStartedAt(Instant.now().minusSeconds(20));
            setField(docInvocation, "attemptCount", 1);

            when(generationRunRepository.findById(run.getId())).thenReturn(Optional.of(run));
            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(generationRunRepository.save(any(GenerationRun.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(kafkaSubmissionService.submit(any(), any(), any())).thenReturn(ackResult());

            sagaExecutionService.handleAgentCompletion(
                    run.getId(), AgentType.DOCUMENTATION, true,
                    "{\"doc\": \"complete\"}", Instant.now());

            // Verify assembly signal published to project-state-changes topic
            verify(kafkaSubmissionService).submit(
                    eq(KafkaTopics.PROJECT_STATE_CHANGES),
                    eq(projectId.toString()),
                    payloadCaptor.capture());

            Object assemblyPayload = payloadCaptor.getAllValues().stream()
                    .filter(p -> p instanceof BlueprintAssemblySignal)
                    .findFirst()
                    .orElse(null);

            assertThat(assemblyPayload).isNotNull();
            BlueprintAssemblySignal signal = (BlueprintAssemblySignal) assemblyPayload;
            assertThat(signal.generationRunId()).isEqualTo(run.getId());
            assertThat(signal.projectId()).isEqualTo(projectId);
        }

        @Test
        @DisplayName("on retry: publishes RETRY progress event")
        void onRetry_publishesRetryEvent() {
            UUID projectId = UUID.randomUUID();
            GenerationRun run = createRunWithAllInvocations(projectId);
            run.setStatus(GenerationRunStatus.RUNNING);

            AgentInvocation reqInvocation = findInvocation(run, AgentType.REQUIREMENT_ANALYST);
            reqInvocation.setStatus(InvocationStatus.RUNNING);
            setField(reqInvocation, "attemptCount", 1);

            when(generationRunRepository.findById(run.getId())).thenReturn(Optional.of(run));
            when(agentInvocationRepository.save(any(AgentInvocation.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(kafkaSubmissionService.submit(any(), any(), any())).thenReturn(ackResult());

            sagaExecutionService.handleAgentCompletion(
                    run.getId(), AgentType.REQUIREMENT_ANALYST, false, null, Instant.now());

            // Verify RETRY progress event published
            verify(kafkaSubmissionService, times(2)).submit(
                    eq(KafkaTopics.AGENT_PROGRESS), any(), payloadCaptor.capture());

            List<Object> progressPayloads = payloadCaptor.getAllValues();
            ProgressEvent retryEvent = progressPayloads.stream()
                    .filter(p -> p instanceof ProgressEvent)
                    .map(p -> (ProgressEvent) p)
                    .filter(e -> e.eventType() == ProgressEvent.EventType.RETRY)
                    .findFirst()
                    .orElse(null);

            assertThat(retryEvent).isNotNull();
            assertThat(retryEvent.agentType()).isEqualTo(AgentType.REQUIREMENT_ANALYST);
            assertThat(retryEvent.attemptCount()).isEqualTo(1);
        }
    }
}
