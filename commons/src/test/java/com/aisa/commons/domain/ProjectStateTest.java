package com.aisa.commons.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the permitted-transition invariant (Property 7, Requirements 3.9–3.10).
 */
class ProjectStateTest {

    @Test
    void permitsDefinedForwardTransitions() {
        assertTrue(ProjectState.DRAFT.canTransitionTo(ProjectState.ANALYZING));
        assertTrue(ProjectState.ANALYZING.canTransitionTo(ProjectState.GENERATING));
        assertTrue(ProjectState.GENERATING.canTransitionTo(ProjectState.IN_REVIEW));
        assertTrue(ProjectState.IN_REVIEW.canTransitionTo(ProjectState.APPROVED));
        assertTrue(ProjectState.IN_REVIEW.canTransitionTo(ProjectState.CHANGES_REQUESTED));
        assertTrue(ProjectState.CHANGES_REQUESTED.canTransitionTo(ProjectState.GENERATING));
    }

    @Test
    void permitsArchiveFromAnyNonArchivedState() {
        for (ProjectState s : ProjectState.values()) {
            if (s != ProjectState.ARCHIVED) {
                assertTrue(s.canTransitionTo(ProjectState.ARCHIVED), s + " should allow archive");
            }
        }
    }

    @Test
    void rejectsIllegalTransitions() {
        assertFalse(ProjectState.DRAFT.canTransitionTo(ProjectState.APPROVED));
        assertFalse(ProjectState.APPROVED.canTransitionTo(ProjectState.GENERATING));
        assertFalse(ProjectState.ARCHIVED.canTransitionTo(ProjectState.DRAFT));
        assertFalse(ProjectState.GENERATING.canTransitionTo(ProjectState.APPROVED));
    }
}
