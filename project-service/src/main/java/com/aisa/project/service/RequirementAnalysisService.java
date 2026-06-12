package com.aisa.project.service;

import com.aisa.commons.domain.ProjectState;
import com.aisa.project.ai.AiAnalysisClient;
import com.aisa.project.ai.AiAnalysisException;
import com.aisa.project.ai.AnalysisResult;
import com.aisa.project.config.AnalysisConfig;
import com.aisa.project.domain.Idea;
import com.aisa.project.domain.Project;
import com.aisa.project.domain.Requirement;
import com.aisa.project.domain.RequirementType;
import com.aisa.project.domain.UseCase;
import com.aisa.project.repository.ProjectRepository;
import com.aisa.project.security.ProjectPrincipal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates requirement analysis for a Project (Requirements 4.1, 4.2, 4.5, 4.6).
 *
 * <p>Initiates analysis by:
 * <ol>
 *   <li>Transitioning the Project state to {@link ProjectState#ANALYZING} via the
 *       state machine (Requirement 4.6).</li>
 *   <li>Calling the AI provider (via {@link AiAnalysisClient}) with the Project's Idea
 *       description.</li>
 *   <li>Parsing the AI response into {@link Requirement} entities (≥1 FR + ≥1 NFR,
 *       Requirement 4.1) and {@link UseCase} entities with traceability links
 *       (Requirement 4.5).</li>
 * </ol>
 *
 * <p>If the AI provider fails, analysis halts and the Project's prior state and any
 * existing requirements are preserved (Requirement 4.9).
 */
@Service
public class RequirementAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(RequirementAnalysisService.class);

    private final ProjectRepository projectRepository;
    private final ProjectStateMachineService stateMachineService;
    private final AiAnalysisClient aiAnalysisClient;
    private final AnalysisConfig analysisConfig;

    public RequirementAnalysisService(ProjectRepository projectRepository,
                                      ProjectStateMachineService stateMachineService,
                                      AiAnalysisClient aiAnalysisClient,
                                      AnalysisConfig analysisConfig) {
        this.projectRepository = projectRepository;
        this.stateMachineService = stateMachineService;
        this.aiAnalysisClient = aiAnalysisClient;
        this.analysisConfig = analysisConfig;
    }

    /**
     * Initiates requirement analysis for a Project: transitions state to ANALYZING,
     * calls AI with retry, and persists generated requirements and use cases.
     *
     * @param projectId the Project to analyze
     * @param principal the authenticated principal initiating analysis
     * @return the updated Project with generated requirements and use cases
     * @throws ProjectNotFoundException        if the Project is absent or not accessible
     * @throws InvalidStateTransitionException if the Project cannot transition to ANALYZING
     * @throws AiAnalysisException             if the AI provider fails after retries
     */
    @Transactional
    public Project analyzeProject(UUID projectId, ProjectPrincipal principal) {
        // Step 1: Transition state to ANALYZING (Requirement 4.6).
        Project project = stateMachineService.transition(projectId, ProjectState.ANALYZING, principal);

        // Step 2: Retrieve the Idea description for AI analysis.
        Idea idea = project.getIdea();
        if (idea == null || idea.getDescription() == null || idea.getDescription().isBlank()) {
            throw new AiAnalysisException("Project has no idea description to analyze");
        }

        // Step 3: Call the AI provider with retry logic (Requirement 4.9).
        // Retry up to maxRetries (default 3) total attempts. On exhaustion, halt analysis,
        // preserve prior state and existing requirements, and return provider-failure error.
        AnalysisResult result = invokeAiWithRetry(idea.getDescription(), projectId);

        // Step 4: Validate minimum constraints (≥1 FR + ≥1 NFR, Requirement 4.1).
        validateAnalysisResult(result);

        // Step 5: Persist generated requirements.
        List<Requirement> requirements = result.requirements().stream()
                .map(gr -> {
                    RequirementType type = parseRequirementType(gr.type());
                    Requirement req = new Requirement(project, gr.statement(), type);
                    return req;
                })
                .toList();
        project.getRequirements().addAll(requirements);

        // Step 6: Persist use cases with traceability links (Requirement 4.5).
        for (AnalysisResult.GeneratedUseCase guc : result.useCases()) {
            UseCase useCase = new UseCase(project, guc.title(), guc.description());
            // Link to the generated requirements by index (traceability).
            for (Integer idx : guc.requirementIndices()) {
                if (idx >= 0 && idx < requirements.size()) {
                    useCase.getRequirements().add(requirements.get(idx));
                }
            }
            project.getUseCases().add(useCase);
        }

        return projectRepository.save(project);
    }

    /**
     * Invokes the AI analysis client with retry logic (Requirement 4.9).
     *
     * <p>Retries up to {@link AnalysisConfig#getMaxRetries()} total attempts (default 3).
     * Both provider errors ({@link AiAnalysisException}) and timeouts count as failed attempts.
     * On exhaustion of all retries, throws a descriptive {@link AiAnalysisException} with
     * a "provider-failure" indication so the caller can preserve prior state.
     *
     * @param ideaDescription the idea text to analyze
     * @param projectId       the project identifier (for logging)
     * @return the analysis result from the AI provider
     * @throws AiAnalysisException if all retry attempts are exhausted
     */
    private AnalysisResult invokeAiWithRetry(String ideaDescription, UUID projectId) {
        int maxAttempts = analysisConfig.getMaxRetries();
        long timeoutSeconds = analysisConfig.getTimeoutSeconds();
        AiAnalysisException lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                AnalysisResult result = CompletableFuture
                        .supplyAsync(() -> aiAnalysisClient.analyze(ideaDescription))
                        .get(timeoutSeconds, TimeUnit.SECONDS);
                log.info("AI analysis succeeded on attempt {}/{} for project {}",
                        attempt, maxAttempts, projectId);
                return result;
            } catch (TimeoutException ex) {
                lastException = new AiAnalysisException(
                        "AI analysis timed out after " + timeoutSeconds + " seconds on attempt "
                                + attempt + "/" + maxAttempts, ex);
                log.warn("AI analysis timed out on attempt {}/{} for project {}",
                        attempt, maxAttempts, projectId);
            } catch (ExecutionException ex) {
                Throwable cause = ex.getCause();
                if (cause instanceof AiAnalysisException aiEx) {
                    lastException = aiEx;
                } else {
                    lastException = new AiAnalysisException(
                            "AI analysis failed on attempt " + attempt + "/" + maxAttempts, cause);
                }
                log.warn("AI analysis failed on attempt {}/{} for project {}: {}",
                        attempt, maxAttempts, projectId, lastException.getMessage());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AiAnalysisException("AI analysis was interrupted", ex);
            }
        }

        // All retries exhausted: halt analysis, preserve prior state (Requirement 4.9).
        log.error("AI provider failed after {} attempts for project {}. Halting analysis.",
                maxAttempts, projectId);
        throw new AiAnalysisException(
                "provider-failure: AI provider failed after " + maxAttempts
                        + " attempts. Analysis halted, prior state preserved.", lastException);
    }

    private void validateAnalysisResult(AnalysisResult result) {
        if (result == null || result.requirements() == null || result.requirements().isEmpty()) {
            throw new AiAnalysisException("AI analysis produced no requirements");
        }

        boolean hasFr = result.requirements().stream()
                .anyMatch(r -> "FUNCTIONAL".equalsIgnoreCase(r.type()));
        boolean hasNfr = result.requirements().stream()
                .anyMatch(r -> "NON_FUNCTIONAL".equalsIgnoreCase(r.type()));

        if (!hasFr) {
            throw new AiAnalysisException("AI analysis must produce at least one functional requirement");
        }
        if (!hasNfr) {
            throw new AiAnalysisException("AI analysis must produce at least one non-functional requirement");
        }

        if (result.useCases() == null || result.useCases().isEmpty()) {
            throw new AiAnalysisException("AI analysis must produce at least one use case");
        }
    }

    private RequirementType parseRequirementType(String type) {
        if ("FUNCTIONAL".equalsIgnoreCase(type)) {
            return RequirementType.FUNCTIONAL;
        }
        return RequirementType.NON_FUNCTIONAL;
    }
}
