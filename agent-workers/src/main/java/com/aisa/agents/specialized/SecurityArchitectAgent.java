package com.aisa.agents.specialized;

import com.aisa.agents.framework.AbstractAgent;
import com.aisa.agents.framework.AgentResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Security Architect Agent — produces security design.
 *
 * <p>Takes the Software Architect's output and produces authentication, authorization,
 * data protection sections with threats and mitigations.
 *
 * <p>Requirements 12.1–12.7: AuthN/authZ/data-protection sections, threats+mitigations,
 * at-rest and in-transit encryption, RBAC model, ≤120s, missing-input error.
 */
@Component
public class SecurityArchitectAgent extends AbstractAgent {

    public SecurityArchitectAgent() {
        super("security-architect.txt");
    }

    @Override
    public String agentType() {
        return "SECURITY_ARCHITECT";
    }

    @Override
    public String buildPrompt(Map<String, String> prerequisiteOutputs) {
        String softwareArchitecture = prerequisiteOutputs.getOrDefault("SOFTWARE_ARCHITECT", "");
        if (softwareArchitecture.isBlank()) {
            throw new IllegalArgumentException("SOFTWARE_ARCHITECT output is required");
        }
        String template = getPromptTemplate();
        return substitute(template, "PREREQUISITES", softwareArchitecture);
    }

    @Override
    public AgentResult processResponse(String aiResponse) {
        try {
            String json = extractJson(aiResponse);
            JsonNode root = parseJson(json);

            // Validate authentication section
            requireObject(root, "authentication");

            // Validate authorization section with RBAC model
            requireObject(root, "authorization");
            JsonNode authz = root.get("authorization");
            requireField(authz, "model");

            // Validate data protection section
            requireObject(root, "dataProtection");
            JsonNode dp = root.get("dataProtection");
            requireField(dp, "atRest");
            requireField(dp, "inTransit");

            // Validate threats
            requireNonEmptyArray(root, "threats");
            for (JsonNode threat : root.get("threats")) {
                requireField(threat, "threat");
                requireField(threat, "mitigation");
            }

            return new AgentResult.Success(json);

        } catch (AgentValidationException e) {
            return new AgentResult.Failure("Validation failed: " + e.getMessage());
        } catch (Exception e) {
            return new AgentResult.Failure("Failed to process response: " + e.getMessage());
        }
    }
}
