package com.aisa.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aisa.commons.domain.ProjectState;
import com.aisa.commons.domain.Role;
import com.aisa.project.domain.Project;
import com.aisa.project.domain.ProjectStateTransition;
import com.aisa.project.repository.ProjectRepository;
import com.aisa.project.repository.ProjectStateTransitionRepository;
import com.aisa.project.security.ProjectPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ProjectStateMachineService}: valid transition succeeds and
 * records a {@link ProjectStateTransition} (Requirement 3.8), invalid transition is
 * rejected with state preserved (Requirement 3.10), and archive from any state
 * (Requirement 3.5).
 */
@ExtendWith(MockitoExtension.class)
class ProjectStateMachineServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectStateTransitionRepository transitionRepository;

    private ProjectStateMachineService service;

    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProjectStateMachineService(projectRepository, transitionRepository);
    }

    private ProjectPrincipal owner() {
        return new ProjectPrincipal(ownerId, Role.PRODUCT_MANAGER);
    }

    private Project projectInState(ProjectState state) {
        Project project = new Project("Test Project", "Description", ownerId);
        project.setState(state);
        return project;
    }

    @Test
    void validTransitionUpdatesStateAndRecordsTransition() {
        UUID projectId = UUID.randomUUID();
        Project project = projectInState(ProjectState.DRAFT);
        when(projectRepository.findByIdAndOwnerId(projectId, ownerId))
                .thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transitionRepository.save(any(ProjectStateTransition.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Project result = service.transition(projectId, ProjectState.ANALYZING, owner());

        // State is updated (Requirement 3.9).
        assertThat(result.getState()).isEqualTo(ProjectState.ANALYZING);

        // A transition record is persisted with from/to/initiatedBy (Requirement 3.8).
        ArgumentCaptor<ProjectStateTransition> captor =
                ArgumentCaptor.forClass(ProjectStateTransition.class);
        verify(transitionRepository).save(captor.capture());
        ProjectStateTransition recorded = captor.getValue();
        assertThat(recorded.getFromState()).isEqualTo(ProjectState.DRAFT);
        assertThat(recorded.getToState()).isEqualTo(ProjectState.ANALYZING);
        assertThat(recorded.getInitiatedBy()).isEqualTo(ownerId);
        assertThat(recorded.getProject()).isSameAs(project);
    }

    @Test
    void invalidTransitionRejectedWithStatePreserved() {
        UUID projectId = UUID.randomUUID();
        Project project = projectInState(ProjectState.DRAFT);
        when(projectRepository.findByIdAndOwnerId(projectId, ownerId))
                .thenReturn(Optional.of(project));

        // DRAFT -> APPROVED is not in the permitted set (Requirement 3.10).
        assertThatThrownBy(() -> service.transition(projectId, ProjectState.APPROVED, owner()))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("DRAFT")
                .hasMessageContaining("APPROVED");

        // State is preserved — no save is called.
        assertThat(project.getState()).isEqualTo(ProjectState.DRAFT);
        verify(projectRepository, never()).save(any());
        verify(transitionRepository, never()).save(any());
    }

    @Test
    void archiveIsPermittedFromAnyNonArchivedState() {
        UUID projectId = UUID.randomUUID();
        Project project = projectInState(ProjectState.IN_REVIEW);
        when(projectRepository.findByIdAndOwnerId(projectId, ownerId))
                .thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transitionRepository.save(any(ProjectStateTransition.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Project result = service.transition(projectId, ProjectState.ARCHIVED, owner());

        assertThat(result.getState()).isEqualTo(ProjectState.ARCHIVED);
        verify(transitionRepository).save(any(ProjectStateTransition.class));
    }

    @Test
    void archiveFromArchivedIsRejected() {
        UUID projectId = UUID.randomUUID();
        Project project = projectInState(ProjectState.ARCHIVED);
        when(projectRepository.findByIdAndOwnerId(projectId, ownerId))
                .thenReturn(Optional.of(project));

        // ARCHIVED -> ARCHIVED is not permitted.
        assertThatThrownBy(() -> service.transition(projectId, ProjectState.ARCHIVED, owner()))
                .isInstanceOf(InvalidStateTransitionException.class);

        assertThat(project.getState()).isEqualTo(ProjectState.ARCHIVED);
        verify(projectRepository, never()).save(any());
    }

    @Test
    void transitionOnNonExistentProjectRaisesNotFound() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findByIdAndOwnerId(projectId, ownerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transition(projectId, ProjectState.ANALYZING, owner()))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void changesRequestedToGeneratingIsPermitted() {
        UUID projectId = UUID.randomUUID();
        Project project = projectInState(ProjectState.CHANGES_REQUESTED);
        when(projectRepository.findByIdAndOwnerId(projectId, ownerId))
                .thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transitionRepository.save(any(ProjectStateTransition.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Project result = service.transition(projectId, ProjectState.GENERATING, owner());

        assertThat(result.getState()).isEqualTo(ProjectState.GENERATING);
    }

    @Test
    void inReviewToChangesRequestedIsPermitted() {
        UUID projectId = UUID.randomUUID();
        Project project = projectInState(ProjectState.IN_REVIEW);
        when(projectRepository.findByIdAndOwnerId(projectId, ownerId))
                .thenReturn(Optional.of(project));
        when(projectRepository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
        when(transitionRepository.save(any(ProjectStateTransition.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Project result = service.transition(projectId, ProjectState.CHANGES_REQUESTED, owner());

        assertThat(result.getState()).isEqualTo(ProjectState.CHANGES_REQUESTED);
    }
}
