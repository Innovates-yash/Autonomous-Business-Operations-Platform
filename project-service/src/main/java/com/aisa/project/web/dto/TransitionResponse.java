package com.aisa.project.web.dto;

import com.aisa.commons.domain.ProjectState;
import com.aisa.project.domain.Project;
import java.time.Instant;
import java.util.UUID;

/**
 * Response payload after a successful state transition (Requirement 3.8). Carries
 * the Project's updated state along with its identity and audit timestamps.
 *
 * @param id          the Project identifier
 * @param name        the Project name
 * @param fromState   the state before the transition
 * @param toState     the state after the transition (current state)
 * @param ownerId     identifier of the owning User
 * @param updatedAt   last-update timestamp (reflects the transition)
 */
public record TransitionResponse(
        UUID id,
        String name,
        ProjectState fromState,
        ProjectState toState,
        UUID ownerId,
        Instant updatedAt) {

    public static TransitionResponse from(Project project, ProjectState fromState) {
        return new TransitionResponse(
                project.getId(),
                project.getName(),
                fromState,
                project.getState(),
                project.getOwnerId(),
                project.getUpdatedAt());
    }
}
