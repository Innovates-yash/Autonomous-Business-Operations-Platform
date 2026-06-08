package com.aisa.commons.domain;

import java.util.Map;
import java.util.Set;

/**
 * Project lifecycle states and the permitted transitions between them
 * (Requirements 3.4, 3.9, 19). Any transition not present in {@link #ALLOWED}
 * must be rejected with the current state preserved (Requirement 3.10).
 */
public enum ProjectState {
    DRAFT,
    ANALYZING,
    GENERATING,
    IN_REVIEW,
    APPROVED,
    CHANGES_REQUESTED,
    ARCHIVED;

    /** Permitted forward transitions. Archiving is allowed from any non-archived state. */
    public static final Map<ProjectState, Set<ProjectState>> ALLOWED = Map.of(
            DRAFT, Set.of(ANALYZING, ARCHIVED),
            ANALYZING, Set.of(GENERATING, ARCHIVED),
            GENERATING, Set.of(IN_REVIEW, ARCHIVED),
            IN_REVIEW, Set.of(APPROVED, CHANGES_REQUESTED, ARCHIVED),
            CHANGES_REQUESTED, Set.of(GENERATING, ARCHIVED),
            APPROVED, Set.of(ARCHIVED),
            ARCHIVED, Set.of()
    );

    /**
     * @return true if a transition from this state to {@code target} is permitted.
     */
    public boolean canTransitionTo(ProjectState target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }
}
