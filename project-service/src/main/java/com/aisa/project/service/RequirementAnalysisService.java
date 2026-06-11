package com.aisa.project.service;

import com.aisa.commons.domain.ProjectState;
import com.aisa.project.ai.AiAnalysisClient;
import com.aisa.project.ai.AiAnalysisException;
import com.aisa.project.ai.AnalysisResult;
import com.aisa.project.domain.Idea;
import com.aisa.project.domain.Project;
import com.aisa.project.domain.Requirement;
import com.aisa.project.domain.RequirementType;
import com.aisa.project.domain.UseCase;
import com.aisa.project.repository.ProjectRepository;
import com.aisa.project.security.ProjectPrincipal;
import java.util.List;
import java.util.UUID;
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

    public RequirementAnalysisService(ProjectRepository projectRepository,
                                      ProjectStateMachineService stateMachineService,
                                      AiAnalysisClient aiAnalysisClient) {
        this.projectRepository = projectRepository;
        this.stateMachineService = stateMachineService;
        this.aiAnalysisClient = aiAnalysisClient;
    }

    /**
     * Initiates requirement analysis for a Project: transitions state to ANALYZING,
     * calls AI, and persists generated requirements and use cases.
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

        // Step 3: Call the AI provider to generate requirements and use cases (Requirement 4.1).
        AnalysisResult result;
        try {
            result = aiAnalysisClient.analyze(idea.getDescription());
        } catch (AiAnalysisException ex) {
            // On AI failure: preserve prior state and existing requirements (Requirement 4.9).
            // Rollback the state transition by re-transitioning is complex; instead we let
            // the transaction roll back (the state change was within this TX).
            throw ex;
        } catch (Exception ex) {
            log.error("Unexpected error during AI analysis for project {}", projectId, ex);
            throw new AiAnalysisException("AI analysis failed unexpectedly", ex);
        }

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
