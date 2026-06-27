package com.aisa.agents.specialized;

import com.aisa.agents.framework.AbstractAgent;
import com.aisa.agents.framework.AgentResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Database Architect Agent — produces entity-relationship models and caching strategy.
 *
 * <p>Takes the Software Architect's output and produces entities with attributes and keys,
 * relationships with cardinality, and Redis caching strategy.
 *
 * <p>Requirements 11.1–11.7: entities, relationships with cardinality, ER design,
 * key attributes, Redis caching, undetermined-cardinality handling, empty-input result.
 */
@Component
public class DatabaseArchitectAgent extends AbstractAgent {

    public DatabaseArchitectAgent() {
        super("database-architect.txt");
    }

    @Override
    public String agentType() {
        return "DATABASE_ARCHITECT";
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

            // Validate entities
            requireNonEmptyArray(root, "entities");
            for (JsonNode entity : root.get("entities")) {
                requireField(entity, "name");
                requireField(entity, "attributes");
            }

            // Validate relationships
            requireNonEmptyArray(root, "relationships");
            for (JsonNode relationship : root.get("relationships")) {
                requireField(relationship, "from");
                requireField(relationship, "to");
                requireField(relationship, "cardinality");
            }

            // Validate caching strategy
            requireObject(root, "cachingStrategy");

            return new AgentResult.Success(json);

        } catch (AgentValidationException e) {
            return new AgentResult.Failure("Validation failed: " + e.getMessage());
        } catch (Exception e) {
            return new AgentResult.Failure("Failed to process response: " + e.getMessage());
        }
    }
}
