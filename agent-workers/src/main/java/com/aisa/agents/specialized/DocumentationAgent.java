package com.aisa.agents.specialized;

import com.aisa.agents.framework.AbstractAgent;
import com.aisa.agents.framework.AgentResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Documentation Agent — compiles all artifacts into human-readable documentation.
 *
 * <p>Takes outputs from DevOps Architect and Cost Estimation and produces a complete
 * document with executive summary, table of contents, and titled sections.
 *
 * <p>Requirements 16.1–16.5: compile all artifacts as distinct titled sections once,
 * TOC matching section order, executive summary 100–1000 words, missing-output error,
 * 3-attempt failure handling.
 */
@Component
public class DocumentationAgent extends AbstractAgent {

    private static final int MIN_SUMMARY_WORDS = 100;
    private static final int MAX_SUMMARY_WORDS = 1000;

    public DocumentationAgent() {
        super("documentation.txt");
    }

    @Override
    public String agentType() {
        return "DOCUMENTATION";
    }

    @Override
    public String buildPrompt(Map<String, String> prerequisiteOutputs) {
        String devOps = prerequisiteOutputs.getOrDefault("DEVOPS_ARCHITECT", "");
        String costEstimation = prerequisiteOutputs.getOrDefault("COST_ESTIMATION", "");

        if (devOps.isBlank() || costEstimation.isBlank()) {
            throw new IllegalArgumentException(
                    "DEVOPS_ARCHITECT and COST_ESTIMATION outputs are both required");
        }

        String template = getPromptTemplate();
        template = substitute(template, "DEVOPS_ARCHITECT", devOps);
        template = substitute(template, "COST_ESTIMATION", costEstimation);
        return template;
    }

    @Override
    public AgentResult processResponse(String aiResponse) {
        try {
            String json = extractJson(aiResponse);
            JsonNode root = parseJson(json);

            // Validate executive summary
            requireField(root, "executiveSummary");
            String summary = root.get("executiveSummary").asText();
            int wordCount = summary.trim().split("\\s+").length;
            if (wordCount < MIN_SUMMARY_WORDS) {
                return new AgentResult.Failure(
                        "Executive summary too short: " + wordCount + " words (minimum "
                                + MIN_SUMMARY_WORDS + ")");
            }
            if (wordCount > MAX_SUMMARY_WORDS) {
                return new AgentResult.Failure(
                        "Executive summary too long: " + wordCount + " words (maximum "
                                + MAX_SUMMARY_WORDS + ")");
            }

            // Validate table of contents
            requireNonEmptyArray(root, "tableOfContents");

            // Validate sections
            requireNonEmptyArray(root, "sections");
            for (JsonNode section : root.get("sections")) {
                requireField(section, "title");
                requireField(section, "content");
            }

            // Validate TOC matches sections
            Set<String> tocTitles = new HashSet<>();
            for (JsonNode tocEntry : root.get("tableOfContents")) {
                tocTitles.add(tocEntry.asText());
            }
            Set<String> sectionTitles = new HashSet<>();
            for (JsonNode section : root.get("sections")) {
                sectionTitles.add(section.get("title").asText());
            }
            if (!tocTitles.equals(sectionTitles)) {
                return new AgentResult.Failure(
                        "Table of contents does not match section titles. "
                                + "TOC: " + tocTitles + ", Sections: " + sectionTitles);
            }

            return new AgentResult.Success(json);

        } catch (AgentValidationException e) {
            return new AgentResult.Failure("Validation failed: " + e.getMessage());
        } catch (Exception e) {
            return new AgentResult.Failure("Failed to process response: " + e.getMessage());
        }
    }
}
