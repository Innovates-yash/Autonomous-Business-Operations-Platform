package com.aisa.orchestrator.saga;

import com.aisa.orchestrator.domain.AgentInvocation;
import com.aisa.orchestrator.domain.AgentType;
import com.aisa.orchestrator.domain.GenerationRun;
import com.aisa.orchestrator.domain.GenerationRunStatus;
import com.aisa.orchestrator.domain.InvocationStatus;
import com.aisa.orchestrator.repository.AgentInvocationRepository;
import com.aisa.orchestrator.repository.GenerationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AgentTimeoutScheduler} verifying:
 * <ul>
 *   <li>120s timeout detection marks invocations for timeout handling</li>
 *   <li>Only RUNNING invocations belonging to RUNNING runs are processed</li>
 *   <li>Invocations on completed/failed runs are skipped</li>
 *   <li>Exceptions in individual timeout handling don't affect other invocations</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgentTimeoutSchedulerTest {

    private static final long TIMEOUT_SECONDS = 120;

    @Mock
    private AgentInvocationRepository agentInvocationRepository;

    @Mock
    private GenerationRunRepository generationRunRepository;

    @Mock
    private SagaExecutionService sagaExecutionService;

    private AgentTimeoutScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new AgentTimeoutScheduler(
                agentInvocationRepository,
                generationRunRepository,
                sagaExecutionService,
                TIMEOUT_SECONDS
        );
    }

    @Test
    @DisplayName("Configured timeout matches 120 seconds (Req 6.4)")
    void timeoutConfiguredTo120Seconds() {
        assertThat(scheduler.getTimeoutSeconds()).isEqualTo(120);
    }

    @Nested
    @DisplayName("Scan for timed-out invocations")
    class ScanTests {

        @Test
        @DisplayName("No stalled invocations — no action taken")
        void noStalledInvocations() {
            when(agentInvocationRepository.findByStatusAndStartedAtBefore(
                    eq(InvocationStatus.RUNNING), any(Instant.class)))
                    .thenReturn(List.of());

            scheduler.scanForTimedOutInvocations();

            verify(sagaExecutionService, never()).handleTimeout(any(), any());
        }

        @Test
        @DisplayName("Stalled RUNNING invocation on RUNNING run triggers handleTimeout")
        void stalledInvocationTriggersTimeout() {
            GenerationRun run = createRunningRun();
            AgentInvocation stalled = createStalledInvocation(run, AgentType.REQUIREMENT_ANALYST);

            when(agentInvocationRepository.findByStatusAndStartedAtBefore(
                    eq(InvocationStatus.RUNNING), any(Instant.class)))
                    .thenReturn(List.of(stalled));

            scheduler.scanForTimedOutInvocations();

            verify(sagaExecutionService).handleTimeout(run, stalled);
        }

        @Test
        @DisplayName("Multiple stalled invocations are each processed")
        void multipleStalledInvocationsProcessed() {
            GenerationRun run = createRunningRun();
            AgentInvocation stalled1 = createStalledInvocation(run, AgentType.REQUIREMENT_ANALYST);
            AgentInvocation stalled2 = createStalledInvocation(run, AgentType.SOFTWARE_ARCHITECT);

            when(agentInvocationRepository.findByStatusAndStartedAtBefore(
                    eq(InvocationStatus.RUNNING), any(Instant.class)))
                    .thenReturn(List.of(stalled1, stalled2));

            scheduler.scanForTimedOutInvocations();

            verify(sagaExecutionService).handleTimeout(run, stalled1);
            verify(sagaExecutionService).handleTimeout(run, stalled2);
        }

        @Test
        @DisplayName("Stalled invocation on COMPLETED run is skipped")
        void stalledInvocationOnCompletedRunSkipped() {
            GenerationRun completedRun = createRun(GenerationRunStatus.COMPLETED);
            AgentInvocation stalled = createStalledInvocation(completedRun, AgentType.REQUIREMENT_ANALYST);

            when(agentInvocationRepository.findByStatusAndStartedAtBefore(
                    eq(InvocationStatus.RUNNING), any(Instant.class)))
                    .thenReturn(List.of(stalled));

            scheduler.scanForTimedOutInvocations();

            verify(sagaExecutionService, never()).handleTimeout(any(), any());
        }

        @Test
        @DisplayName("Stalled invocation on FAILED run is skipped")
        void stalledInvocationOnFailedRunSkipped() {
            GenerationRun failedRun = createRun(GenerationRunStatus.FAILED);
            AgentInvocation stalled = createStalledInvocation(failedRun, AgentType.REQUIREMENT_ANALYST);

            when(agentInvocationRepository.findByStatusAndStartedAtBefore(
                    eq(InvocationStatus.RUNNING), any(Instant.class)))
                    .thenReturn(List.of(stalled));

            scheduler.scanForTimedOutInvocations();

            verify(sagaExecutionService, never()).handleTimeout(any(), any());
        }

        @Test
        @DisplayName("Exception in one timeout doesn't prevent processing others")
        void exceptionInOneDoesNotBlockOthers() {
            GenerationRun run = createRunningRun();
            AgentInvocation stalled1 = createStalledInvocation(run, AgentType.REQUIREMENT_ANALYST);
            AgentInvocation stalled2 = createStalledInvocation(run, AgentType.SOFTWARE_ARCHITECT);

            when(agentInvocationRepository.findByStatusAndStartedAtBefore(
                    eq(InvocationStatus.RUNNING), any(Instant.class)))
                    .thenReturn(List.of(stalled1, stalled2));

            // First invocation throws, second should still be processed
            org.mockito.Mockito.doThrow(new RuntimeException("test error"))
                    .when(sagaExecutionService).handleTimeout(run, stalled1);

            scheduler.scanForTimedOutInvocations();

            verify(sagaExecutionService).handleTimeout(run, stalled1);
            verify(sagaExecutionService).handleTimeout(run, stalled2);
        }

        @Test
        @DisplayName("Cutoff time is calculated relative to now minus timeout")
        void cutoffTimeCalculatedCorrectly() {
            when(agentInvocationRepository.findByStatusAndStartedAtBefore(
                    eq(InvocationStatus.RUNNING), any(Instant.class)))
                    .thenReturn(List.of());

            Instant before = Instant.now().minusSeconds(TIMEOUT_SECONDS + 1);
            scheduler.scanForTimedOutInvocations();
            Instant after = Instant.now().minusSeconds(TIMEOUT_SECONDS - 1);

            ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(agentInvocationRepository).findByStatusAndStartedAtBefore(
                    eq(InvocationStatus.RUNNING), cutoffCaptor.capture());

            Instant capturedCutoff = cutoffCaptor.getValue();
            assertThat(capturedCutoff).isAfterOrEqualTo(before);
            assertThat(capturedCutoff).isBeforeOrEqualTo(after);
        }
    }

    // --- Test helpers ---

    private GenerationRun createRunningRun() {
        return createRun(GenerationRunStatus.RUNNING);
    }

    private GenerationRun createRun(GenerationRunStatus status) {
        GenerationRun run = mock(GenerationRun.class);
        when(run.getStatus()).thenReturn(status);
        when(run.getId()).thenReturn(java.util.UUID.randomUUID());
        return run;
    }

    private AgentInvocation createStalledInvocation(GenerationRun run, AgentType agentType) {
        AgentInvocation invocation = mock(AgentInvocation.class);
        when(invocation.getGenerationRun()).thenReturn(run);
        when(invocation.getAgentType()).thenReturn(agentType);
        when(invocation.getStatus()).thenReturn(InvocationStatus.RUNNING);
        when(invocation.getStartedAt()).thenReturn(Instant.now().minusSeconds(TIMEOUT_SECONDS + 30));
        when(invocation.getId()).thenReturn(java.util.UUID.randomUUID());
        return invocation;
    }
}
