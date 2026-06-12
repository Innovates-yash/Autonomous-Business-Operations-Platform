package com.aisa.orchestrator.kafka;

import com.aisa.commons.kafka.KafkaTopics;
import com.aisa.orchestrator.saga.SagaExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that listens on the {@code agent-progress} topic for agent
 * completion events and delegates to the {@link SagaExecutionService}.
 *
 * <p>When an agent worker completes (success or failure), it publishes an
 * {@link AgentCompletionEvent} to this topic. This consumer:
 * <ul>
 *   <li>Marks the corresponding {@link com.aisa.orchestrator.domain.AgentInvocation} complete</li>
 *   <li>Persists the {@link com.aisa.orchestrator.domain.AgentOutput} (Req 6.6, 26.6)</li>
 *   <li>Triggers the next wave of ready agents (Req 6.1, 6.10)</li>
 * </ul>
 */
@Component
public class AgentProgressConsumer {

    private static final Logger log = LoggerFactory.getLogger(AgentProgressConsumer.class);

    private final SagaExecutionService sagaExecutionService;

    public AgentProgressConsumer(SagaExecutionService sagaExecutionService) {
        this.sagaExecutionService = sagaExecutionService;
    }

    /**
     * Consumes agent completion events from the agent-progress Kafka topic.
     *
     * <p>Each event triggers the saga to advance: marking invocations, persisting outputs,
     * and dispatching the next ready agents or halting dependents on failure.
     *
     * @param event the agent completion event from a worker
     */
    @KafkaListener(
            topics = KafkaTopics.AGENT_PROGRESS,
            groupId = "orchestrator-saga",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAgentCompletion(AgentCompletionEvent event) {
        log.info("Received agent completion: agent={}, run={}, success={}",
                event.agentType(), event.generationRunId(), event.success());

        try {
            sagaExecutionService.handleAgentCompletion(
                    event.generationRunId(),
                    event.agentType(),
                    event.success(),
                    event.outputContent(),
                    event.completedAt()
            );
        } catch (Exception e) {
            log.error("Error processing agent completion event for run={}, agent={}: {}",
                    event.generationRunId(), event.agentType(), e.getMessage(), e);
            // Re-throw to trigger Kafka retry/DLQ mechanism
            throw e;
        }
    }
}
