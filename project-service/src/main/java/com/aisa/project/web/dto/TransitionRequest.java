package com.aisa.project.web.dto;

import com.aisa.commons.domain.ProjectState;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for a Project state transition. The caller specifies only the
 * desired target state; the service validates the transition from the Project's
 * current state against the permitted set (Requirements 3.9, 3.10).
 *
 * @param targetState the requested new state for the Project
 */
public record TransitionRequest(
        @NotNull(message = "Target state is required")
        ProjectState targetState
) {
}
