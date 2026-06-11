package com.aisa.project.ai;

import java.util.List;
import org.springframework.stereotype.Component;

/**
 * A deterministic stub implementation of {@link AiAnalysisClient} that produces a
 * fixed set of requirements and use cases for testing and development without a
 * live AI provider. Produces at least 1 FR, 1 NFR, and 1 use case with a
 * requirement link, satisfying the minimum guarantees of Requirements 4.1 and 4.5.
 *
 * <p>In production this bean will be superseded by the real AI Provider Gateway
 * client via profile-based conditional configuration.
 */
@Component
public class StubAiAnalysisClient implements AiAnalysisClient {

    @Override
    public AnalysisResult analyze(String ideaDescription) {
        List<AnalysisResult.GeneratedRequirement> requirements = List.of(
                new AnalysisResult.GeneratedRequirement(
                        "The system shall allow users to " + truncate(ideaDescription, 80),
                        "FUNCTIONAL"),
                new AnalysisResult.GeneratedRequirement(
                        "The system shall respond to user requests within 2 seconds under normal load",
                        "NON_FUNCTIONAL"),
                new AnalysisResult.GeneratedRequirement(
                        "The system shall provide a RESTful API for all core operations",
                        "FUNCTIONAL")
        );

        List<AnalysisResult.GeneratedUseCase> useCases = List.of(
                new AnalysisResult.GeneratedUseCase(
                        "Primary User Interaction",
                        "User interacts with the system to accomplish the core business goal: "
                                + truncate(ideaDescription, 100),
                        List.of(0, 2)),
                new AnalysisResult.GeneratedUseCase(
                        "Performance Monitoring",
                        "System administrator monitors response times to ensure quality of service",
                        List.of(1))
        );

        return new AnalysisResult(requirements, useCases);
    }

    private static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
