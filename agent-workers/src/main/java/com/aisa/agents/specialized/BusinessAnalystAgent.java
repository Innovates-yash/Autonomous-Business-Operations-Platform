package com.aisa.agents.specialized;

import com.aisa.agents.framework.AbstractAgent;
import com.aisa.agents.framework.AgentResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Business Analyst Agent — analyzes business context from requirements.
 *
 * <p>Takes the Requirement Analyst's output and produces stakeholder analysis,
 * value drivers, constraints, and assumptions with traceability.
 *
 * <p>Requirements 8.1–8.5: stakeholders with role+interest, value drivers linked
 * to stakeholders, constraints/assumptions with traceability, missing-input error,
 * 3-attempt failure handling.
 */
@Component
public class BusinessAnalystAgent extends AbstractAgent {

    public BusinessAnalystAgent() {
        super("business-analyst.txt");
    }

    @Override
    public String agentType() {
        return "BUSINESS_ANALYST";
    }

    @Override
    public String buildPrompt(Map<String, String> prerequisiteOutputs) {
        String requirements = prerequisiteOutputs.getOrDefault("REQUIREMENT_ANALYST", "");
        if (requirements.isBlank()) {
            throw new IllegalArgumentException("REQUIREMENT_ANALYST output is required");
        }
        String template = getPromptTemplate();
        return substitute(template, "PREREQUISITES", requirements);
    }

    @Override
    public AgentResult processResponse(String aiResponse) {
        try {
            String json = extractJson(aiResponse);
            JsonNode root = parseJson(json);

            // Validate stakeholders
            requireNonEmptyArray(root, "stakeholders");
            for (JsonNode stakeholder : root.get("stakeholders")) {
                requireField(stakeholder, "role");
                requireField(stakeholder, "interest");
            }

            // Validate value drivers
            requireNonEmptyArray(root, "valueDrivers");
            for (JsonNode driver : root.get("valueDrivers")) {
                requireField(driver, "name");
                requireField(driver, "linkedStakeholders");
            }

            // Constraints and assumptions are optional but if present must have traceability
            if (root.has("constraints") && root.get("constraints").isArray()) {
                for (JsonNode constraint : root.get("constraints")) {
                    requireField(constraint, "description");
                }
            }

            if (root.has("assumptions") && root.get("assumptions").isArray()) {
                for (JsonNode assumption : root.get("assumptions")) {
                    requireField(assumption, "description");
                }
            }

            return new AgentResult.Success(json);

        } catch (AgentValidationException e) {
            return new AgentResult.Failure("Validation failed: " + e.getMessage());
        } catch (Exception e) {
            return new AgentResult.Failure("Failed to process response: " + e.getMessage());
        }
    }
}
