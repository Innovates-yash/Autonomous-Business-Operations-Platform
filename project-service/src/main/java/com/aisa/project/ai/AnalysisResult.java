package com.aisa.project.ai;

import java.util.List;

/**
 * The structured result produced by the AI analysis of a Project's Idea. Contains
 * the generated requirements (each with a statement and classification) and use cases
 * (each with a title, description, and links to requirement indices).
 *
 * <p>This is the uniform contract between the AI client abstraction and the
 * {@link com.aisa.project.service.RequirementAnalysisService}. Provider-specific
 * parsing is encapsulated behind the {@link AiAnalysisClient} interface.
 */
public record AnalysisResult(
        List<GeneratedRequirement> requirements,
        List<GeneratedUseCase> useCases
) {

    /**
     * A single generated requirement with its type classification.
     *
     * @param statement the declarative statement of one observable system behavior or quality attribute
     * @param type      either "FUNCTIONAL" or "NON_FUNCTIONAL"
     */
    public record GeneratedRequirement(String statement, String type) {
    }

    /**
     * A generated use case linked to one or more requirements by index (0-based into
     * the {@link #requirements()} list).
     *
     * @param title              a short use case title
     * @param description        the use case description
     * @param requirementIndices 0-based indices into the requirements list that this use case traces to
     */
    public record GeneratedUseCase(String title, String description, List<Integer> requirementIndices) {
    }
}
