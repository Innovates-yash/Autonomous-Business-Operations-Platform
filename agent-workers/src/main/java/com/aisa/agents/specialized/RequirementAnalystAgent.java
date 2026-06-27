package com.aisa.agents.specialized;

import com.aisa.agents.framework.AbstractAgent;
import com.aisa.agents.framework.AgentResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Requirement Analyst Agent — the root agent in the dependency DAG.
 *
 * <p>Takes a raw business idea (provided via the task context, not via prerequisite outputs)
 * and produces a structured list of functional and non-functional requirements, each with
 * a unique id, classification, description, assumptions, and clarifying questions.
 *
 * <p>Requirements 7.1–7.5: ≥1 FR, ≥1 NFR, unique IDs, assumptions, error on empty input.
 */
@Component
public class RequirementAnalystAgent extends AbstractAgent {

    public RequirementAnalystAgent() {
        super("requirement-analyst.txt");
    }

    @Override
    public String agentType() {
        return "REQUIREMENT_ANALYST";
    }

    @Override
    public String buildPrompt(Map<String, String> prerequisiteOutputs) {
        // The Requirement Analyst is the root agent — it receives the project idea
        // as context via prerequisite outputs under a special key, or as the sole entry.
        String ideaContext = prerequisiteOutputs.getOrDefault("IDEA", "");
        if (ideaContext.isBlank()) {
            // Fallback: use any available prerequisite as the idea
            ideaContext = prerequisiteOutputs.values().stream()
                    .filter(v -> v != null && !v.isBlank())
                    .findFirst()
                    .orElse("");
        }

        String template = getPromptTemplate();
        return substitute(template, "IDEA", ideaContext);
    }

    @Override
    public AgentResult processResponse(String aiResponse) {
        try {
            String json = extractJson(aiResponse);
            JsonNode root = parseJson(json);

            // Validate required structure
            requireNonEmptyArray(root, "requirements");

            // Validate FR/NFR classification
            JsonNode requirements = root.get("requirements");
            boolean hasFR = false;
            boolean hasNFR = false;

            for (JsonNode req : requirements) {
                requireField(req, "id");
                requireField(req, "type");
                requireField(req, "description");

                String type = req.get("type").asText();
                if ("FR".equalsIgnoreCase(type) || "FUNCTIONAL".equalsIgnoreCase(type)) {
                    hasFR = true;
                } else if ("NFR".equalsIgnoreCase(type) || "NON_FUNCTIONAL".equalsIgnoreCase(type)
                        || "NON-FUNCTIONAL".equalsIgnoreCase(type)) {
                    hasNFR = true;
                }
            }

            if (!hasFR) {
                return new AgentResult.Failure(
                        "Requirements must contain at least one Functional Requirement (FR)");
            }
            if (!hasNFR) {
                return new AgentResult.Failure(
                        "Requirements must contain at least one Non-Functional Requirement (NFR)");
            }

            return new AgentResult.Success(json);

        } catch (AgentValidationException e) {
            return new AgentResult.Failure("Validation failed: " + e.getMessage());
        } catch (Exception e) {
            return new AgentResult.Failure("Failed to process response: " + e.getMessage());
        }
    }
}
