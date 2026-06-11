package com.aisa.project.service;

import com.aisa.commons.domain.ProjectState;

/**
 * Raised when a requested state transition is not among the permitted transitions
 * for the Project's current state (Requirement 3.10). The Project's current state
 * is preserved; no change is applied.
 */
public class InvalidStateTransitionException extends RuntimeException {

    private final ProjectState currentState;
    private final ProjectState requestedState;

    public InvalidStateTransitionException(ProjectState currentState, ProjectState requestedState) {
        super("Transition from " + currentState + " to " + requestedState + " is not permitted");
        this.currentState = currentState;
        this.requestedState = requestedState;
    }

    public ProjectState getCurrentState() {
        return currentState;
    }

    public ProjectState getRequestedState() {
        return requestedState;
    }
}
