package com.aisa.agents.specialized;

import com.aisa.agents.framework.AbstractAgent;
import com.aisa.agents.framework.AgentResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Software Architect Agent — decomposes the solution into components and microservices.
 *
 * <p>Takes the Product Manager's output and produces components with responsibilities,
 * microservices with technology choices, and interactions with sync/async style and
 * event-driven descriptions.
 *
 * <p>Requirements 10.1–10.6: components, microservices, interactions, completeness check,
 * missing-input error.
 */
@Component
public class SoftwareArchitectAgent extends AbstractAgent {

    public SoftwareArchitectAgent() {
        super("software-architect.txt");
    }

    @Override
    public String agentType() {
        return "SOFTWARE_ARCHITECT";
    }

    @Override
    public String buildPrompt(Map<String, String> prerequisiteOutputs) {
        String productManagement = prerequisiteOutputs.getOrDefault("PRODUCT_MANAGER", "");
        if (productManagement.isBlank()) {
            throw new IllegalArgumentException("PRODUCT_MANAGER output is required");
        }
        String template = getPromptTemplate();
        return substitute(template, "PREREQUISITES", productManagement);
    }

    @Override
    public AgentResult processResponse(String aiResponse) {
        try {
            String json = extractJson(aiResponse);
            JsonNode root = parseJson(json);

            // Validate components
            requireNonEmptyArray(root, "components");
            for (JsonNode component : root.get("components")) {
                requireField(component, "name");
                requireField(component, "responsibility");
            }

            // Validate microservices
            requireNonEmptyArray(root, "microservices");
            for (JsonNode service : root.get("microservices")) {
                requireField(service, "name");
                requireField(service, "responsibilities");
                requireField(service, "technology");
            }

            // Validate interactions
            requireNonEmptyArray(root, "interactions");
            for (JsonNode interaction : root.get("interactions")) {
                requireField(interaction, "from");
                requireField(interaction, "to");
                requireField(interaction, "style");
            }

            return new AgentResult.Success(json);

        } catch (AgentValidationException e) {
            return new AgentResult.Failure("Validation failed: " + e.getMessage());
        } catch (Exception e) {
            return new AgentResult.Failure("Failed to process response: " + e.getMessage());
        }
    }
}
