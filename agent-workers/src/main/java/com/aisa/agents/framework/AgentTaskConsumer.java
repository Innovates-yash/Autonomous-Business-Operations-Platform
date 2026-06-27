package com.aisa.agents.framework;

import com.aisa.commons.kafka.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that listens on the {@code agent-tasks} topic and dispatches work
 * to the appropriate {@link SpecializedAgent}.
 *
 * <p>Implements the complete-or-error contract (Requirement 7.1): every consumed task
 * always produces an {@link AgentCompletionEvent} on the {@code agent-progress} topic,
 * either with {@code success=true} and validated content, or {@code success=false} with
 * a descriptive error. No message is ever silently dropped.
 *
 * <p>Flow:
 * <ol>
 *   <li>Deserialize {@link AgentTaskMessage} from Kafka</li>
 *   <li>Look up the {@link SpecializedAgent} via {@link AgentRegistry}</li>
 *   <li>Build the prompt via {@link SpecializedAgent#buildPrompt}</li>
 *   <li>Call the AI Provider Gateway via {@link ProviderGatewayClient#complete}</li>
 *   <li>Validate the response via {@link SpecializedAgent#processResponse}</li>
 *   <li>Publish the completion event</li>
 * </ol>
 */
@Component
public class AgentTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskConsumer.class);

    private final AgentRegistry agentRegistry;
    private final ProviderGatewayClient providerGatewayClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public AgentTaskConsumer(AgentRegistry agentRegistry,
                             ProviderGatewayClient providerGatewayClient,
                             KafkaTemplate<String, Object> kafkaTemplate) {
        this.agentRegistry = agentRegistry;
        this.providerGatewayClient = providerGatewayClient;
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Consumes agent task messages from the {@code agent-tasks} Kafka topic.
     *
     * <p>Each message is processed end-to-end: prompt construction → AI call →
     * output validation → completion event. Errors at any stage produce a failure
     * event rather than being silently dropped.
     *
     * @param task the agent task message from the orchestrator
     */
    @KafkaListener(
            topics = KafkaTopics.AGENT_TASKS,
            groupId = "agent-workers",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onAgentTask(AgentTaskMessage task) {
        log.info("Received agent task: agent={}, run={}, invocation={}",
                task.agentType(), task.generationRunId(), task.invocationId());

        AgentCompletionEvent event = processTask(task);

        log.info("Publishing completion event: agent={}, run={}, success={}",
                event.agentType(), event.generationRunId(), event.success());

        kafkaTemplate.send(KafkaTopics.AGENT_PROGRESS,
                task.generationRunId().toString(), event);
    }

    /**
     * Process a single agent task, always returning a completion event.
     * This method is package-private for testing.
     *
     * @param task the agent task message
     * @return a completion event — always non-null, always either success or failure
     */
    AgentCompletionEvent processTask(AgentTaskMessage task) {
        try {
            // 1. Look up the agent
            SpecializedAgent agent = agentRegistry.find(task.agentType())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown agent type: " + task.agentType()));

            // 2. Build the prompt
            String prompt = agent.buildPrompt(task.prerequisiteOutputs());
            log.debug("Built prompt for {}: {} chars", task.agentType(), prompt.length());

            // 3. Call the AI Provider Gateway
            String aiResponse = providerGatewayClient.complete(prompt);
            log.debug("Received AI response for {}: {} chars", task.agentType(), aiResponse.length());

            // 4. Validate the response
            AgentResult result = agent.processResponse(aiResponse);

            // 5. Return the appropriate completion event
            return switch (result) {
                case AgentResult.Success success -> AgentCompletionEvent.success(
                        task.generationRunId(), task.invocationId(),
                        task.agentType(), success.validatedJson()
                );
                case AgentResult.Failure failure -> AgentCompletionEvent.failure(
                        task.generationRunId(), task.invocationId(),
                        task.agentType(), failure.errorMessage()
                );
            };

        } catch (ProviderGatewayClient.ProviderGatewayException e) {
            log.error("Provider Gateway error for agent={}, run={}: {}",
                    task.agentType(), task.generationRunId(), e.getMessage());
            return AgentCompletionEvent.failure(
                    task.generationRunId(), task.invocationId(),
                    task.agentType(), "Provider Gateway error: " + e.getMessage()
            );

        } catch (Exception e) {
            log.error("Unexpected error processing agent task: agent={}, run={}: {}",
                    task.agentType(), task.generationRunId(), e.getMessage(), e);
            return AgentCompletionEvent.failure(
                    task.generationRunId(), task.invocationId(),
                    task.agentType(), "Unexpected error: " + e.getMessage()
            );
        }
    }
}
