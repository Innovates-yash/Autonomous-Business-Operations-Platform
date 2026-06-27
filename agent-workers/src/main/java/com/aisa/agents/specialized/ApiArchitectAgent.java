package com.aisa.agents.specialized;

import com.aisa.agents.framework.AbstractAgent;
import com.aisa.agents.framework.AgentResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * API Architect Agent — produces API design and service boundaries.
 *
 * <p>Takes the Software Architect's output and produces service boundaries with
 * operations, request/response shapes, and authentication/authorization requirements.
 *
 * <p>Requirements 13.1–13.5: service boundaries, ≥1 operation each,
 * request/response field shapes, authN/authZ per boundary, missing-info error.
 */
@Component
public class ApiArchitectAgent extends AbstractAgent {

    public ApiArchitectAgent() {
        super("api-architect.txt");
    }

    @Override
    public String agentType() {
        return "API_ARCHITECT";
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

            // Validate service boundaries
            requireNonEmptyArray(root, "serviceBoundaries");
            for (JsonNode boundary : root.get("serviceBoundaries")) {
                requireField(boundary, "name");

                // Each boundary must have at least one operation
                requireNonEmptyArray(boundary, "operations");
                for (JsonNode op : boundary.get("operations")) {
                    requireField(op, "method");
                    requireField(op, "path");
                }

                // Auth fields per boundary
                requireField(boundary, "authentication");
                requireField(boundary, "authorization");
            }

            return new AgentResult.Success(json);

        } catch (AgentValidationException e) {
            return new AgentResult.Failure("Validation failed: " + e.getMessage());
        } catch (Exception e) {
            return new AgentResult.Failure("Failed to process response: " + e.getMessage());
        }
    }
}
