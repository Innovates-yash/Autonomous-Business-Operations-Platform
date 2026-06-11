package com.aisa.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aisa.commons.domain.ProjectState;
import com.aisa.commons.domain.Role;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link RequirementAnalysisService}: validates that analysis produces
 * ≥1 FR + ≥1 NFR (Requirement 4.1), state transitions to ANALYZING (Requirement 4.6),
 * use cases are linked to requirements (Requirement 4.5), and AI failures are handled
 * (Requirement 4.9).
 */
@ExtendWith(MockitoExtension.class)
class RequirementAnalysisServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectStateMachineService stateMachineService;

    @Mock
    private AiAnalysisClient aiAnalysisClient;

    private RequirementAnalysisService service;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RequirementAnalysisService(projectRepository, stateMachineService, aiAnalysisClient);
    }

    private ProjectPrincipal owner() {
        return new ProjectPrincipal(ownerId, Role.PRODUCT_MANAGER);
    }

    private Project projectWithIdea() {
        Project project = new Project("Test Project", "Build a Food Delivery App", ownerId);
        project.setState(ProjectState.ANALYZING);
        Idea idea = new Idea("Test Project", "Build a Food Delivery App");
        idea.setProject(project);
        project.setIdea(idea);
        return project;
    }

    private AnalysisResult validAnalysisResult() {
        List<AnalysisResult.GeneratedRequirement> requirements = List.of(
                new AnalysisResult.GeneratedRequirement(
                        "The system shall allow users to order food online", "FUNCTIONAL"),
                new AnalysisResult.GeneratedRequirement(
                        "The system shall respond within 2 seconds", "NON_FUNCTIONAL")
        );
        List<AnalysisResult.GeneratedUseCase> useCases = List.of(
                new AnalysisResult.GeneratedUseCase(
                        "Order Food", "User orders food from a restaurant", List.of(0))
        );
        return new AnalysisResult(requirements, useCases);
    }

    @Test
    void analyzeProjectProducesAtLeastOneFrAndOneNfr() {
        // Requirement 4.1: analysis must produce ≥1 FR + ≥1 NFR.
        Project project = projectWithIdea();
        when(stateMachineService.transition(eq(projectId), eq(ProjectState.ANALYZING), any()))
                .thenReturn(project);
        when(aiAnalysisClient.analyze("Build a Food Delivery App"))
                .thenReturn(validAnalysisResult());
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project result = service.analyzeProject(projectId, owner());

        List<Requirement> requirements = result.getRequirements();
        assertThat(requirements).hasSizeGreaterThanOrEqualTo(2);

        long frCount = requirements.stream()
                .filter(r -> r.getType() == RequirementType.FUNCTIONAL).count();
        long nfrCount = requirements.stream()
                .filter(r -> r.getType() == RequirementType.NON_FUNCTIONAL).count();
        assertThat(frCount).isGreaterThanOrEqualTo(1);
        assertThat(nfrCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void analyzeProjectTransitionsStateToAnalyzing() {
        // Requirement 4.6: Project transitions to ANALYZING state.
        Project project = projectWithIdea();
        when(stateMachineService.transition(eq(projectId), eq(ProjectState.ANALYZING), any()))
                .thenReturn(project);
        when(aiAnalysisClient.analyze("Build a Food Delivery App"))
                .thenReturn(validAnalysisResult());
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project result = service.analyzeProject(projectId, owner());

        // Verify that the state machine transition was called to move to ANALYZING.
        verify(stateMachineService).transition(projectId, ProjectState.ANALYZING, owner());
        assertThat(result.getState()).isEqualTo(ProjectState.ANALYZING);
    }

    @Test
    void analyzeProjectCreatesUseCasesLinkedToRequirements() {
        // Requirement 4.5: use cases reference at least one functional requirement.
        Project project = projectWithIdea();
        when(stateMachineService.transition(eq(projectId), eq(ProjectState.ANALYZING), any()))
                .thenReturn(project);
        when(aiAnalysisClient.analyze("Build a Food Delivery App"))
                .thenReturn(validAnalysisResult());
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project result = service.analyzeProject(projectId, owner());

        List<UseCase> useCases = result.getUseCases();
        assertThat(useCases).isNotEmpty();

        // Each use case must have at least one requirement linked.
        for (UseCase uc : useCases) {
            assertThat(uc.getRequirements()).isNotEmpty();
        }

        // Verify traceability: the use case links to the functional requirement.
        UseCase firstUseCase = useCases.get(0);
        assertThat(firstUseCase.getRequirements()).anyMatch(
                r -> r.getType() == RequirementType.FUNCTIONAL);
    }

    @Test
    void analyzeProjectPersistsRequirementStatements() {
        // Requirement 4.2: each FR is a single statement defining one observable behavior.
        Project project = projectWithIdea();
        when(stateMachineService.transition(eq(projectId), eq(ProjectState.ANALYZING), any()))
                .thenReturn(project);
        when(aiAnalysisClient.analyze("Build a Food Delivery App"))
                .thenReturn(validAnalysisResult());
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        Project result = service.analyzeProject(projectId, owner());

        List<Requirement> requirements = result.getRequirements();
        for (Requirement req : requirements) {
            assertThat(req.getStatement()).isNotBlank();
            assertThat(req.getProject()).isSameAs(project);
        }
    }

    @Test
    void analyzeProjectFailsWhenAiProviderThrows() {
        // Requirement 4.9: AI failure halts analysis and preserves prior state.
        Project project = projectWithIdea();
        when(stateMachineService.transition(eq(projectId), eq(ProjectState.ANALYZING), any()))
                .thenReturn(project);
        when(aiAnalysisClient.analyze("Build a Food Delivery App"))
                .thenThrow(new AiAnalysisException("Provider timeout after 3 retries"));

        assertThatThrownBy(() -> service.analyzeProject(projectId, owner()))
                .isInstanceOf(AiAnalysisException.class)
                .hasMessageContaining("Provider timeout");

        // Project is not saved (transaction will roll back).
        verify(projectRepository, never()).save(any());
    }

    @Test
    void analyzeProjectFailsWhenNoIdea() {
        // Analysis requires an Idea description.
        Project project = new Project("Test", "Desc", ownerId);
        project.setState(ProjectState.ANALYZING);
        // No idea set.
        when(stateMachineService.transition(eq(projectId), eq(ProjectState.ANALYZING), any()))
                .thenReturn(project);

        assertThatThrownBy(() -> service.analyzeProject(projectId, owner()))
                .isInstanceOf(AiAnalysisException.class)
                .hasMessageContaining("no idea description");
    }

    @Test
    void analyzeProjectFailsWhenAiReturnsNoFunctionalRequirement() {
        // Validation: must have ≥1 FR.
        Project project = projectWithIdea();
        when(stateMachineService.transition(eq(projectId), eq(ProjectState.ANALYZING), any()))
                .thenReturn(project);
        AnalysisResult onlyNfr = new AnalysisResult(
                List.of(new AnalysisResult.GeneratedRequirement("Performance req", "NON_FUNCTIONAL")),
                List.of(new AnalysisResult.GeneratedUseCase("UC", "desc", List.of(0)))
        );
        when(aiAnalysisClient.analyze("Build a Food Delivery App")).thenReturn(onlyNfr);

        assertThatThrownBy(() -> service.analyzeProject(projectId, owner()))
                .isInstanceOf(AiAnalysisException.class)
                .hasMessageContaining("functional requirement");
    }

    @Test
    void analyzeProjectFailsWhenAiReturnsNoNonFunctionalRequirement() {
        // Validation: must have ≥1 NFR.
        Project project = projectWithIdea();
        when(stateMachineService.transition(eq(projectId), eq(ProjectState.ANALYZING), any()))
                .thenReturn(project);
        AnalysisResult onlyFr = new AnalysisResult(
                List.of(new AnalysisResult.GeneratedRequirement("Users can order", "FUNCTIONAL")),
                List.of(new AnalysisResult.GeneratedUseCase("UC", "desc", List.of(0)))
        );
        when(aiAnalysisClient.analyze("Build a Food Delivery App")).thenReturn(onlyFr);

        assertThatThrownBy(() -> service.analyzeProject(projectId, owner()))
                .isInstanceOf(AiAnalysisException.class)
                .hasMessageContaining("non-functional requirement");
    }
}
