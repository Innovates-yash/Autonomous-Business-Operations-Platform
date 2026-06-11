package com.aisa.project.ai;

/**
 * Pluggable interface for AI-driven requirement analysis. Implementations call the
 * AI Provider Gateway (or a stub for tests) and return a structured
 * {@link AnalysisResult} containing functional/non-functional requirements and use
 * cases with traceability.
 *
 * <p>The contract mirrors the design's Requirement Analysis Module interface:
 * {@code analyze(idea) → {requirements, useCases}}. Retries and failover are the
 * responsibility of the implementation (Requirement 4.9).
 */
public interface AiAnalysisClient {

    /**
     * Analyzes the given idea description and produces structured requirements and use cases.
     *
     * @param ideaDescription the plain-language business idea description
     * @return a structured analysis result containing ≥1 FR, ≥1 NFR, and ≥1 use case
     * @throws AiAnalysisException if the AI provider fails after exhausting retries
     */
    AnalysisResult analyze(String ideaDescription);
}
