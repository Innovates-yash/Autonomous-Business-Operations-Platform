package com.aisa.agents.framework;

import java.util.Map;

/**
 * Contract that each of the ten specialized AI agents must implement.
 *
 * <p>The framework calls these methods in order:
 * <ol>
 *   <li>{@link #buildPrompt(Map)} — assemble the prompt from prerequisite outputs</li>
 *   <li>Send the prompt to the AI Provider Gateway for completion</li>
 *   <li>{@link #processResponse(String)} — parse and validate the AI response</li>
 * </ol>
 *
 * <p>This separation lets each agent define its own prompt construction and output
 * validation while the common framework handles Kafka consumption, HTTP calls,
 * error handling, and completion event publishing.
 */
public interface SpecializedAgent {

    /**
     * Returns the agent type name, matching the orchestrator's {@code AgentType} enum value
     * (e.g. {@code "REQUIREMENT_ANALYST"}, {@code "BUSINESS_ANALYST"}).
     *
     * @return the agent type identifier
     */
    String agentType();

    /**
     * Build the prompt string to send to the AI provider.
     *
     * @param prerequisiteOutputs map of prerequisite agent type names to their validated
     *                            JSON output content. Empty for root agents (e.g. Requirement Analyst).
     * @return the complete prompt including system instructions and prerequisite context
     */
    String buildPrompt(Map<String, String> prerequisiteOutputs);

    /**
     * Parse and validate the AI provider's response.
     *
     * <p>Implementations check that the response contains all required JSON sections,
     * non-empty fields, and structurally valid data. Returns {@link AgentResult.Success}
     * with the validated JSON on success, or {@link AgentResult.Failure} with a descriptive
     * error message on validation failure.
     *
     * @param aiResponse the raw text response from the AI provider
     * @return a success result with validated JSON, or a failure result with error details
     */
    AgentResult processResponse(String aiResponse);
}
