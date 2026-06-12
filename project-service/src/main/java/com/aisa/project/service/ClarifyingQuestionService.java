package com.aisa.project.service;

import com.aisa.commons.domain.ProjectState;
import com.aisa.project.ai.AiAnalysisClient;
import com.aisa.project.ai.AiAnalysisException;
import com.aisa.project.ai.AnalysisResult;
import com.aisa.project.config.AnalysisConfig;
import com.aisa.project.domain.ClarifyingQuestion;
import com.aisa.project.domain.Project;
import com.aisa.project.domain.Requirement;
import com.aisa.project.domain.RequirementType;
import com.aisa.project.repository.ClarifyingQuestionRepository;
import com.aisa.project.repository.ProjectRepository;
import com.aisa.project.security.ProjectPrincipal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing clarifying questions during requirement analysis
 * (Requirements 4.3, 4.4).
 *
 * <p>Generates 1–10 clarifying questions with references to specific requirements
 * when missing or ambiguous information is detected. When users provide answers,
 * the affected requirements are regenerated via the AI provider.
 */
@Service
public class ClarifyingQuestionService {

    private static final Logger log = LoggerFactory.getLogger(ClarifyingQuestionService.class);

    private static final int MIN_QUESTIONS = 1;
    private static final int MAX_QUESTIONS = 10;

    private final ClarifyingQuestionRepository questionRepository;
    private final ProjectRepository projectRepository;
    private final AiAnalysisClient aiAnalysisClient;
    private final AnalysisConfig analysisConfig;

    public ClarifyingQuestionService(ClarifyingQuestionRepository questionRepository,
                                     ProjectRepository projectRepository,
                                     AiAnalysisClient aiAnalysisClient,
                                     AnalysisConfig analysisConfig) {
        this.questionRepository = questionRepository;
        this.projectRepository = projectRepository;
        this.aiAnalysisClient = aiAnalysisClient;
        this.analysisConfig = analysisConfig;
    }

    /**
     * Generates between 1 and 10 clarifying questions for a project, each
     * referencing the specific requirement or Idea element it pertains to
     * (Requirement 4.3).
     *
     * @param project the project in ANALYZING state
     * @return the generated clarifying questions
     */
    @Transactional
    public List<ClarifyingQuestion> generateQuestions(Project project) {
        if (project.getState() != ProjectState.ANALYZING) {
            throw new InvalidStateTransitionException(project.getState(), ProjectState.ANALYZING);
        }

        List<Requirement> requirements = project.getRequirements();
        String ideaDescription = project.getIdea() != null ? project.getIdea().getDescription() : "";

        // Generate questions based on existing requirements and the idea.
        List<ClarifyingQuestion> generated = buildQuestionsFromRequirements(project, requirements, ideaDescription);

        // Ensure bounds: 1–10 questions.
        if (generated.isEmpty()) {
            // Always produce at least one question referencing the idea.
            ClarifyingQuestion fallback = new ClarifyingQuestion(project,
                    "Can you provide more details about the expected users and scale of this system?");
            generated = List.of(fallback);
        }

        if (generated.size() > MAX_QUESTIONS) {
            generated = generated.subList(0, MAX_QUESTIONS);
        }

        // Persist and add to project.
        for (ClarifyingQuestion q : generated) {
            project.getClarifyingQuestions().add(q);
        }
        projectRepository.save(project);

        return generated;
    }

    /**
     * Retrieves all clarifying questions for a project.
     *
     * @param projectId the project identifier
     * @return the questions ordered by creation time
     */
    @Transactional(readOnly = true)
    public List<ClarifyingQuestion> getQuestions(UUID projectId) {
        return questionRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
    }

    /**
     * Records user answers for clarifying questions and regenerates the affected
     * requirements (Requirement 4.4).
     *
     * @param project   the project in ANALYZING state
     * @param answers   map of question ID to answer text
     * @param principal the authenticated principal
     * @return the updated project with regenerated requirements
     * @throws AiAnalysisException if the AI provider fails during regeneration
     */
    @Transactional
    public Project answerQuestions(Project project, Map<UUID, String> answers, ProjectPrincipal principal) {
        if (project.getState() != ProjectState.ANALYZING) {
            throw new InvalidStateTransitionException(project.getState(), ProjectState.ANALYZING);
        }

        // Record answers on the respective question entities.
        List<ClarifyingQuestion> questions = project.getClarifyingQuestions();
        List<Requirement> affectedRequirements = new java.util.ArrayList<>();

        for (Map.Entry<UUID, String> entry : answers.entrySet()) {
            UUID questionId = entry.getKey();
            String answer = entry.getValue();

            ClarifyingQuestion question = questions.stream()
                    .filter(q -> q.getId().equals(questionId))
                    .findFirst()
                    .orElse(null);

            if (question != null && answer != null && !answer.isBlank()) {
                question.setAnswer(answer);
                // Track the requirement referenced by this question for regeneration.
                if (question.getRequirement() != null) {
                    affectedRequirements.add(question.getRequirement());
                }
            }
        }

        // Regenerate affected requirements using AI (Requirement 4.4).
        if (!affectedRequirements.isEmpty()) {
            regenerateAffectedRequirements(project, affectedRequirements);
        }

        return projectRepository.save(project);
    }

    private void regenerateAffectedRequirements(Project project, List<Requirement> affected) {
        // Build context: idea + answers for regeneration.
        String ideaDescription = project.getIdea() != null ? project.getIdea().getDescription() : "";
        String answeredContext = project.getClarifyingQuestions().stream()
                .filter(q -> q.getAnswer() != null)
                .map(q -> "Q: " + q.getQuestion() + " A: " + q.getAnswer())
                .collect(Collectors.joining("\n"));

        String regenerationInput = ideaDescription + "\n\nAdditional context from clarifications:\n" + answeredContext;

        long timeoutSeconds = analysisConfig.getTimeoutSeconds();
        AnalysisResult result;
        try {
            result = CompletableFuture.supplyAsync(() -> aiAnalysisClient.analyze(regenerationInput))
                    .get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException ex) {
            log.warn("AI regeneration timed out after {}s for project {}", timeoutSeconds, project.getId());
            throw new AiAnalysisException("AI regeneration timed out after " + timeoutSeconds + " seconds");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof AiAnalysisException aiEx) {
                throw aiEx;
            }
            throw new AiAnalysisException("AI regeneration failed unexpectedly", cause);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AiAnalysisException("AI regeneration was interrupted", ex);
        }

        if (result != null && result.requirements() != null) {
            // Update affected requirements with regenerated statements.
            List<AnalysisResult.GeneratedRequirement> regenerated = result.requirements();
            for (int i = 0; i < Math.min(affected.size(), regenerated.size()); i++) {
                Requirement req = affected.get(i);
                AnalysisResult.GeneratedRequirement gen = regenerated.get(i);
                req.setStatement(gen.statement());
                req.setType(parseRequirementType(gen.type()));
            }
        }
    }

    private List<ClarifyingQuestion> buildQuestionsFromRequirements(
            Project project, List<Requirement> requirements, String ideaDescription) {
        List<ClarifyingQuestion> questions = new java.util.ArrayList<>();

        // Generate questions referencing specific requirements.
        for (int i = 0; i < requirements.size() && questions.size() < MAX_QUESTIONS; i++) {
            Requirement req = requirements.get(i);
            ClarifyingQuestion q = new ClarifyingQuestion(project,
                    "Can you clarify the scope and constraints for: " + truncate(req.getStatement(), 100));
            q.setRequirement(req);
            questions.add(q);
        }

        // Add a general question about the idea if space remains.
        if (questions.size() < MAX_QUESTIONS) {
            ClarifyingQuestion general = new ClarifyingQuestion(project,
                    "Are there any additional features or constraints not mentioned in the idea?");
            questions.add(general);
        }

        return questions;
    }

    private RequirementType parseRequirementType(String type) {
        if ("FUNCTIONAL".equalsIgnoreCase(type)) {
            return RequirementType.FUNCTIONAL;
        }
        return RequirementType.NON_FUNCTIONAL;
    }

    private static String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength) + "...";
    }
}
