package com.aisa.agents.specialized;

import com.aisa.agents.framework.AbstractAgent;
import com.aisa.agents.framework.AgentResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * DevOps Architect Agent — produces DevOps/cloud architecture design.
 *
 * <p>Takes outputs from Database Architect, Security Architect, and API Architect
 * and produces cloud architecture, CI/CD pipeline, containerization, monitoring,
 * and HA/DR strategy with RTO/RPO.
 *
 * <p>Requirements 14.1–14.7: cloud architecture, CI/CD stages, containerization/orchestration,
 * monitoring/logging/tracing, HA/DR with RTO/RPO, missing-input and incomplete-artifact errors.
 */
@Component
public class DevOpsArchitectAgent extends AbstractAgent {

    public DevOpsArchitectAgent() {
        super("devops-architect.txt");
    }

    @Override
    public String agentType() {
        return "DEVOPS_ARCHITECT";
    }

    @Override
    public String buildPrompt(Map<String, String> prerequisiteOutputs) {
        String dbArch = prerequisiteOutputs.getOrDefault("DATABASE_ARCHITECT", "");
        String secArch = prerequisiteOutputs.getOrDefault("SECURITY_ARCHITECT", "");
        String apiArch = prerequisiteOutputs.getOrDefault("API_ARCHITECT", "");

        if (dbArch.isBlank() || secArch.isBlank() || apiArch.isBlank()) {
            throw new IllegalArgumentException(
                    "DATABASE_ARCHITECT, SECURITY_ARCHITECT, and API_ARCHITECT outputs are all required");
        }

        String template = getPromptTemplate();
        template = substitute(template, "DATABASE_ARCHITECT", dbArch);
        template = substitute(template, "SECURITY_ARCHITECT", secArch);
        template = substitute(template, "API_ARCHITECT", apiArch);
        return template;
    }

    @Override
    public AgentResult processResponse(String aiResponse) {
        try {
            String json = extractJson(aiResponse);
            JsonNode root = parseJson(json);

            // Validate all 5 required sections
            requireObject(root, "cloudArchitecture");
            requireObject(root, "ciCd");
            requireObject(root, "containerization");
            requireObject(root, "monitoring");
            requireObject(root, "hadr");

            // CI/CD must have stages
            JsonNode ciCd = root.get("ciCd");
            requireNonEmptyArray(ciCd, "stages");

            // HA/DR must have RTO and RPO
            JsonNode hadr = root.get("hadr");
            requireField(hadr, "rto");
            requireField(hadr, "rpo");

            return new AgentResult.Success(json);

        } catch (AgentValidationException e) {
            return new AgentResult.Failure("Validation failed: " + e.getMessage());
        } catch (Exception e) {
            return new AgentResult.Failure("Failed to process response: " + e.getMessage());
        }
    }
}
