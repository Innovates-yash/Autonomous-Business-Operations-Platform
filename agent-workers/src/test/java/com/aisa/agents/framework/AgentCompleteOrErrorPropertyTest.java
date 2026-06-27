package com.aisa.agents.framework;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Property 12: Complete-or-error agent output.
 *
 * <p>Validates that for every possible scenario (valid response, invalid response,
 * provider error, unknown agent, null response), the {@link AgentTaskConsumer}
 * always produces an {@link AgentCompletionEvent} that is either:
 * <ul>
 *   <li>{@code success=true} with non-null, non-empty {@code outputContent}, or</li>
 *   <li>{@code success=false} with non-null, non-empty {@code errorMessage}</li>
 * </ul>
 * <p>It never produces a mixed state (both null, both non-null, or a null event).
 *
 * <p>Validates: Requirements 7.1, 8.4, 9.4, 10.5, 11.6, 12.6, 13.5, 14.6, 15.5, 16.4
 */
@ExtendWith(MockitoExtension.class)
class AgentCompleteOrErrorPropertyTest {

    @Mock
    private ProviderGatewayClient providerGatewayClient;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private AgentTaskConsumer consumer;

    private static final UUID RUN_ID = UUID.randomUUID();
    private static final UUID INVOCATION_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Create a test agent that accepts valid JSON with a "result" field
        SpecializedAgent testAgent = new SpecializedAgent() {
            @Override
            public String agentType() {
                return "TEST_AGENT";
            }

            @Override
            public String buildPrompt(Map<String, String> prerequisiteOutputs) {
                return "Test prompt";
            }

            @Override
            public AgentResult processResponse(String aiResponse) {
                if (aiResponse.contains("\"result\"")) {
                    return new AgentResult.Success(aiResponse);
                }
                return new AgentResult.Failure("Missing required field: result");
            }
        };

        AgentRegistry registry = new AgentRegistry(List.of(testAgent));
        consumer = new AgentTaskConsumer(registry, providerGatewayClient, kafkaTemplate);
    }

    private AgentTaskMessage taskFor(String agentType) {
        return new AgentTaskMessage(RUN_ID, INVOCATION_ID, PROJECT_ID, agentType, Map.of());
    }

    /**
     * Asserts the complete-or-error property on a completion event:
     * exactly one of (success + content) or (failure + error) is set.
     */
    private void assertCompleteOrError(AgentCompletionEvent event) {
        assertThat(event).as("Event must never be null").isNotNull();

        if (event.success()) {
            assertThat(event.outputContent())
                    .as("Successful event must have non-null, non-blank content")
                    .isNotNull()
                    .isNotBlank();
            assertThat(event.errorMessage())
                    .as("Successful event must have null errorMessage")
                    .isNull();
        } else {
            assertThat(event.errorMessage())
                    .as("Failed event must have non-null, non-blank errorMessage")
                    .isNotNull()
                    .isNotBlank();
            assertThat(event.outputContent())
                    .as("Failed event must have null outputContent")
                    .isNull();
        }
    }

    @Test
    @DisplayName("Valid AI response → success=true with content")
    void validResponse_producesSuccess() {
        when(providerGatewayClient.complete(anyString()))
                .thenReturn("{\"result\": \"valid output\"}");

        AgentCompletionEvent event = consumer.processTask(taskFor("TEST_AGENT"));

        assertCompleteOrError(event);
        assertThat(event.success()).isTrue();
    }

    @Test
    @DisplayName("Invalid AI response → success=false with error")
    void invalidResponse_producesFailure() {
        when(providerGatewayClient.complete(anyString()))
                .thenReturn("{\"notTheRightField\": \"data\"}");

        AgentCompletionEvent event = consumer.processTask(taskFor("TEST_AGENT"));

        assertCompleteOrError(event);
        assertThat(event.success()).isFalse();
    }

    @Test
    @DisplayName("Provider error → success=false with error")
    void providerError_producesFailure() {
        when(providerGatewayClient.complete(anyString()))
                .thenThrow(new ProviderGatewayClient.ProviderGatewayException("Timeout"));

        AgentCompletionEvent event = consumer.processTask(taskFor("TEST_AGENT"));

        assertCompleteOrError(event);
        assertThat(event.success()).isFalse();
        assertThat(event.errorMessage()).contains("Provider Gateway error");
    }

    @Test
    @DisplayName("Unknown agent type → success=false with error")
    void unknownAgent_producesFailure() {
        AgentCompletionEvent event = consumer.processTask(taskFor("NONEXISTENT_AGENT"));

        assertCompleteOrError(event);
        assertThat(event.success()).isFalse();
        assertThat(event.errorMessage()).contains("Unknown agent type");
    }

    @Test
    @DisplayName("Runtime exception in provider → success=false with error")
    void runtimeException_producesFailure() {
        when(providerGatewayClient.complete(anyString()))
                .thenThrow(new RuntimeException("Unexpected NPE"));

        AgentCompletionEvent event = consumer.processTask(taskFor("TEST_AGENT"));

        assertCompleteOrError(event);
        assertThat(event.success()).isFalse();
        assertThat(event.errorMessage()).contains("Unexpected error");
    }

    @Test
    @DisplayName("Completion event always has correct metadata")
    void completionEvent_hasCorrectMetadata() {
        when(providerGatewayClient.complete(anyString()))
                .thenReturn("{\"result\": \"valid\"}");

        AgentCompletionEvent event = consumer.processTask(taskFor("TEST_AGENT"));

        assertThat(event.generationRunId()).isEqualTo(RUN_ID);
        assertThat(event.invocationId()).isEqualTo(INVOCATION_ID);
        assertThat(event.agentType()).isEqualTo("TEST_AGENT");
        assertThat(event.completedAt()).isNotNull();
    }
}
