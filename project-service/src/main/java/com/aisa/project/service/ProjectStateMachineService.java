package com.aisa.project.service;

import com.aisa.commons.domain.ProjectState;
import com.aisa.project.domain.Project;
import com.aisa.project.domain.ProjectStateTransition;
import com.aisa.project.repository.ProjectRepository;
import com.aisa.project.repository.ProjectStateTransitionRepository;
import com.aisa.project.security.ProjectPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces the Project lifecycle state machine (Requirements 3.4, 3.5, 3.8, 3.9, 3.10).
 *
 * <p>Validates a requested transition against the permitted set defined in
 * {@link ProjectState#ALLOWED}. If the transition is invalid, the Project's current
 * state is preserved and an {@link InvalidStateTransitionException} is raised
 * (Requirement 3.10). On a valid transition the Project's state is updated and a
 * {@link ProjectStateTransition} record is persisted with the timestamp and the
 * initiating User (Requirement 3.8).
 *
 * <p>Archive (from any non-archived state) preserves Project data for recovery
 * (Requirement 3.5).
 */
@Service
public class ProjectStateMachineService {

    private final ProjectRepository projectRepository;
    private final ProjectStateTransitionRepository transitionRepository;

    public ProjectStateMachineService(ProjectRepository projectRepository,
                                      ProjectStateTransitionRepository transitionRepository) {
        this.projectRepository = projectRepository;
        this.transitionRepository = transitionRepository;
    }

    /**
     * Transitions a Project to the requested target state.
     *
     * @param projectId   the Project identifier
     * @param targetState the requested target state
     * @param principal   the authenticated principal initiating the transition
     * @return the updated Project
     * @throws ProjectNotFoundException         if the Project is absent or not accessible
     * @throws InvalidStateTransitionException  if the transition is not permitted
     */
    @Transactional
    public Project transition(UUID projectId, ProjectState targetState, ProjectPrincipal principal) {
        Project project = requireAccessibleProject(projectId, principal);

        ProjectState currentState = project.getState();

        if (!currentState.canTransitionTo(targetState)) {
            throw new InvalidStateTransitionException(currentState, targetState);
        }

        // Record the transition with timestamp + initiating User (Requirement 3.8).
        ProjectStateTransition record = new ProjectStateTransition(
                project, currentState, targetState, principal.userId());
        transitionRepository.save(record);

        // Update the Project's state.
        project.setState(targetState);
        return projectRepository.save(project);
    }

    /**
     * Retrieves the chronological transition history for a Project.
     *
     * @param projectId the Project identifier
     * @param principal the authenticated principal
     * @return the list of transitions ordered by occurrence time
     * @throws ProjectNotFoundException if the Project is absent or not accessible
     */
    @Transactional(readOnly = true)
    public List<ProjectStateTransition> getTransitionHistory(UUID projectId, ProjectPrincipal principal) {
        requireAccessibleProject(projectId, principal);
        return transitionRepository.findByProjectIdOrderByOccurredAtAsc(projectId);
    }

    private Project requireAccessibleProject(UUID projectId, ProjectPrincipal principal) {
        if (principal.canViewAllProjects()) {
            return projectRepository.findById(projectId)
                    .orElseThrow(() -> new ProjectNotFoundException(projectId));
        }
        return projectRepository.findByIdAndOwnerId(projectId, principal.userId())
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }
}
