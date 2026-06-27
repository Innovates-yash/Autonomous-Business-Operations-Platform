package com.aisa.agents.specialized;

import com.aisa.agents.framework.AbstractAgent;
import com.aisa.agents.framework.AgentResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Cost Estimation Agent — produces cost estimates.
 *
 * <p>Takes outputs from Database Architect, Security Architect, and API Architect
 * and produces per-category costs with min/max ranges, a summed total, and assumptions.
 *
 * <p>Requirements 15.1–15.5: per-category costs + summed total, assumptions
 * (volume/region/period), numeric ranges with single currency, partial estimation,
 * error on no estimate.
 */
@Component
public class CostEstimationAgent extends AbstractAgent {

    public CostEstimationAgent() {
        super("cost-estimation.txt");
    }

    @Override
    public String agentType() {
        return "COST_ESTIMATION";
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

            // Validate categories
            requireNonEmptyArray(root, "categories");
            for (JsonNode category : root.get("categories")) {
                requireField(category, "name");
                requireObject(category, "estimatedCost");
                JsonNode cost = category.get("estimatedCost");
                requireField(cost, "min");
                requireField(cost, "max");
                requireField(cost, "currency");
            }

            // Validate total estimate
            requireObject(root, "totalEstimate");
            JsonNode total = root.get("totalEstimate");
            requireField(total, "min");
            requireField(total, "max");
            requireField(total, "currency");

            // Validate single currency consistency
            String totalCurrency = total.get("currency").asText();
            for (JsonNode category : root.get("categories")) {
                String catCurrency = category.get("estimatedCost").get("currency").asText();
                if (!totalCurrency.equals(catCurrency)) {
                    return new AgentResult.Failure(
                            "Currency mismatch: total uses " + totalCurrency
                                    + " but category '" + category.get("name").asText()
                                    + "' uses " + catCurrency);
                }
            }

            // Validate assumptions
            requireNonEmptyArray(root, "assumptions");

            return new AgentResult.Success(json);

        } catch (AgentValidationException e) {
            return new AgentResult.Failure("Validation failed: " + e.getMessage());
        } catch (Exception e) {
            return new AgentResult.Failure("Failed to process response: " + e.getMessage());
        }
    }
}
