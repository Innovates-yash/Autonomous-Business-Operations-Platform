package com.aisa.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aisa.commons.domain.ProjectState;
import com.aisa.commons.domain.Role;
import com.aisa.project.ai.AiAnalysisClient;
import com.aisa.project.ai.AiAnalysisException;
import com.aisa.project.ai.AnalysisResult;
import com.aisa.project.config.AnalysisConfig;
import com.aisa.project.domain.ClarifyingQuestion;
import com.aisa.project.domain.Idea;
import com.aisa.project.domain.Project;
import com.aisa.project.domain.Requirement;
import com.aisa.project.domain.RequirementType;
import com.aisa.project.repository.ClarifyingQuestionRepository;
import com.aisa.project.repository.ProjectRepository;
import com.aisa.project.security.ProjectPrincipal;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ClarifyingQuestionService}: validates that 1–10 clarifying
 * questions are generated with requirement references (Requirement 4.3), answers
 * trigger affected requirement regeneration (Requirement 4.4), and state constraints
 * are enforced.
 */
@ExtendWith(MockitoExtension.class)
class ClarifyingQuestionServiceTest {

    @Mock
    private ClarifyingQuestionRepository questionRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private AiAnalysisClient aiAnalysisClient;

    private AnalysisConfig analysisConfig;

    private ClarifyingQuestionService service;

    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        analysisConfig = new AnalysisConfig();
        analysisConfig.setTimeoutSeconds(60);
        analysisConfig.setMaxRetries(3);
        service = new ClarifyingQuestionService(questionRepository, projectRepository,
                aiAnalysisClient, analysisConfig);
    }

    private ProjectPrincipal owner() {
        return new ProjectPrincipal(ownerId, Role.PRODUCT_MANAGER);
    }

    private Project analyzingProjectWithRequirements() {
        Project project = new Project("Test Project", "Build a Food Delivery App", ownerId);
        project.setState(ProjectState.ANALYZING);
        Idea idea = new Idea("Test Project", "Build a Food Delivery App");
        idea.setProject(project);
        project.setIdea(idea);

        Requirement fr = new Requirement(project, "Users can order food online", RequirementType.FUNCTIONAL);
        Requirement nfr = new Requirement(project, "Response time < 2s", RequirementType.NON_FUNCTIONAL);
        setId(fr, UUID.randomUUID());
        setId(nfr, UUID.randomUUID());
        project.getRequirements().add(fr);
        project.getRequirements().add(nfr);

        return project;
    }

    // =========================================================================
    // Question Generation Tests (Requirement 4.3)
    // =========================================================================

    @Test
    void generateQuestionsProducesBetweenOneAndTenQuestions() {
        // Requirement 4.3: generate between 1 and 10 clarifying questions.
        Project project = analyzingProjectWithRequirements();
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        List<ClarifyingQuestion> questions = service.generateQuestions(project);

        assertThat(questions).hasSizeBetween(1, 10);
    }

    @Test
    void generateQuestionsReferencesSpecificRequirements() {
        // Requirement 4.3: each question references the specific requirement it pertains to.
        Project project = analyzingProjectWithRequirements();
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        List<ClarifyingQuestion> questions = service.generateQuestions(project);

        // At least some questions should reference requirements.
        long withReference = questions.stream()
                .filter(q -> q.getRequirement() != null)
                .count();
        assertThat(withReference).isGreaterThanOrEqualTo(1);
    }

    @Test
    void generateQuestionsAddsQuestionsToProject() {
        // Questions should be associated with the project.
        Project project = analyzingProjectWithRequirements();
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        service.generateQuestions(project);

        assertThat(project.getClarifyingQuestions()).isNotEmpty();
        assertThat(project.getClarifyingQuestions()).hasSizeBetween(1, 10);
    }

    @Test
    void generateQuestionsProducesAtLeastOneEvenWithNoRequirements() {
        // Should always produce at least 1 question (fallback).
        Project project = new Project("Test", "Build something", ownerId);
        project.setState(ProjectState.ANALYZING);
        Idea idea = new Idea("Test", "Build something");
        idea.setProject(project);
        project.setIdea(idea);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        List<ClarifyingQuestion> questions = service.generateQuestions(project);

        assertThat(questions).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void generateQuestionsThrowsWhenNotInAnalyzingState() {
        // Questions can only be generated while in ANALYZING state.
        Project project = new Project("Test", "Desc", ownerId);
        project.setState(ProjectState.DRAFT);

        assertThatThrownBy(() -> service.generateQuestions(project))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void generateQuestionsDoesNotExceedTen() {
        // Even with many requirements, at most 10 questions.
        Project project = new Project("Test", "Big idea", ownerId);
        project.setState(ProjectState.ANALYZING);
        Idea idea = new Idea("Test", "Big idea");
        idea.setProject(project);
        project.setIdea(idea);
        // Add 15 requirements.
        for (int i = 0; i < 15; i++) {
            Requirement r = new Requirement(project, "Requirement " + i, RequirementType.FUNCTIONAL);
            setId(r, UUID.randomUUID());
            project.getRequirements().add(r);
        }
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        List<ClarifyingQuestion> questions = service.generateQuestions(project);

        assertThat(questions).hasSizeLessThanOrEqualTo(10);
    }

    // =========================================================================
    // Answer Incorporation Tests (Requirement 4.4)
    // =========================================================================

    @Test
    void answerQuestionsRecordsAnswerAndTriggersRegeneration() {
        // Requirement 4.4: answer incorporation regenerates affected requirements.
        Project project = analyzingProjectWithRequirements();
        Requirement req = project.getRequirements().get(0);
        UUID reqId = req.getId();

        ClarifyingQuestion question = new ClarifyingQuestion(project, "What scale?");
        question.setRequirement(req);
        UUID questionId = UUID.randomUUID();
        setId(question, questionId);
        project.getClarifyingQuestions().add(question);

        // The AI should return regenerated requirements.
        AnalysisResult regenResult = new AnalysisResult(
                List.of(new AnalysisResult.GeneratedRequirement(
                        "Users can order food supporting 10000 concurrent users", "FUNCTIONAL")),
                List.of());
        when(aiAnalysisClient.analyze(anyString())).thenReturn(regenResult);
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<UUID, String> answers = Map.of(questionId, "We expect 10000 concurrent users");
        Project updated = service.answerQuestions(project, answers, owner());

        // Verify answer was recorded.
        assertThat(question.getAnswer()).isEqualTo("We expect 10000 concurrent users");

        // Verify AI was called to regenerate (because question references a requirement).
        verify(aiAnalysisClient).analyze(anyString());

        // Verify the affected requirement's statement was updated.
        assertThat(req.getStatement()).contains("10000 concurrent users");
    }

    @Test
    void answerQuestionsThrowsWhenQuestionNotFound() {
        // Should throw QuestionNotFoundException for unknown question ID.
        Project project = analyzingProjectWithRequirements();
        UUID unknownQuestionId = UUID.randomUUID();

        Map<UUID, String> answers = Map.of(unknownQuestionId, "Some answer");

        assertThatThrownBy(() -> service.answerQuestions(project, answers, owner()))
                .isInstanceOf(QuestionNotFoundException.class);
    }

    @Test
    void answerQuestionsThrowsWhenNotInAnalyzingState() {
        // Answers can only be provided while in ANALYZING state.
        Project project = new Project("Test", "Desc", ownerId);
        project.setState(ProjectState.GENERATING);
        UUID questionId = UUID.randomUUID();

        assertThatThrownBy(() -> service.answerQuestions(
                project, Map.of(questionId, "answer"), owner()))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void answerQuestionsDoesNotCallAiWhenNoRequirementReference() {
        // If answered question has no requirement reference, no regeneration needed.
        Project project = analyzingProjectWithRequirements();

        ClarifyingQuestion question = new ClarifyingQuestion(project, "General question?");
        // No requirement reference set.
        UUID questionId = UUID.randomUUID();
        setId(question, questionId);
        project.getClarifyingQuestions().add(question);

        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<UUID, String> answers = Map.of(questionId, "Some general answer");
        service.answerQuestions(project, answers, owner());

        // AI should NOT be called since no affected requirements.
        verify(aiAnalysisClient, never()).analyze(anyString());

        // Answer should still be recorded.
        assertThat(question.getAnswer()).isEqualTo("Some general answer");
    }

    @Test
    void answerQuestionsThrowsOnAiFailureDuringRegeneration() {
        // Requirement 4.4: AI failure during regeneration propagates.
        Project project = analyzingProjectWithRequirements();
        Requirement req = project.getRequirements().get(0);

        ClarifyingQuestion question = new ClarifyingQuestion(project, "Clarify scope?");
        question.setRequirement(req);
        UUID questionId = UUID.randomUUID();
        setId(question, questionId);
        project.getClarifyingQuestions().add(question);

        when(aiAnalysisClient.analyze(anyString()))
                .thenThrow(new AiAnalysisException("Provider unavailable"));

        Map<UUID, String> answers = Map.of(questionId, "More details");

        assertThatThrownBy(() -> service.answerQuestions(project, answers, owner()))
                .isInstanceOf(AiAnalysisException.class);
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private void setId(Object entity, UUID id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set id via reflection", e);
        }
    }
}
