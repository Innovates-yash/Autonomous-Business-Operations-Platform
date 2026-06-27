package com.aisa.agents.specialized;

import com.aisa.agents.framework.AbstractAgent;
import com.aisa.agents.framework.AgentResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Product Manager Agent — produces user stories and use cases.
 *
 * <p>Takes the Business Analyst's output and produces user stories in
 * role-feature-benefit format per functional requirement, use cases mapped
 * to stories, and one priority per story.
 *
 * <p>Requirements 9.1–9.4: user stories, use cases, priorities, error on no requirements.
 */
@Component
public class ProductManagerAgent extends AbstractAgent {

    public ProductManagerAgent() {
        super("product-manager.txt");
    }

    @Override
    public String agentType() {
        return "PRODUCT_MANAGER";
    }

    @Override
    public String buildPrompt(Map<String, String> prerequisiteOutputs) {
        String businessAnalysis = prerequisiteOutputs.getOrDefault("BUSINESS_ANALYST", "");
        if (businessAnalysis.isBlank()) {
            throw new IllegalArgumentException("BUSINESS_ANALYST output is required");
        }
        String template = getPromptTemplate();
        return substitute(template, "PREREQUISITES", businessAnalysis);
    }

    @Override
    public AgentResult processResponse(String aiResponse) {
        try {
            String json = extractJson(aiResponse);
            JsonNode root = parseJson(json);

            // Validate user stories
            requireNonEmptyArray(root, "userStories");
            for (JsonNode story : root.get("userStories")) {
                requireField(story, "id");
                requireField(story, "role");
                requireField(story, "feature");
                requireField(story, "benefit");
                requireField(story, "priority");
            }

            // Validate use cases
            requireNonEmptyArray(root, "useCases");
            for (JsonNode useCase : root.get("useCases")) {
                requireField(useCase, "id");
                requireField(useCase, "title");
                requireField(useCase, "linkedStoryIds");
            }

            return new AgentResult.Success(json);

        } catch (AgentValidationException e) {
            return new AgentResult.Failure("Validation failed: " + e.getMessage());
        } catch (Exception e) {
            return new AgentResult.Failure("Failed to process response: " + e.getMessage());
        }
    }
}
