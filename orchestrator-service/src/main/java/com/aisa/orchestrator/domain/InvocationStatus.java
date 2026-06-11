package com.aisa.orchestrator.domain;

/**
 * Outcome recorded for each {@link AgentInvocation} (Requirement 6.9).
 */
public enum InvocationStatus {

    /** Invocation is queued and awaiting execution. */
    PENDING,

    /** Invocation is currently in progress. */
    RUNNING,

    /** Agent produced a valid output. */
    SUCCESS,

    /** Agent failed to produce a valid output within the allowed attempts. */
    FAILED,

    /** Agent did not respond within the 120-second timeout (Requirement 6.4). */
    TIMED_OUT,

    /** Invocation was halted because a prerequisite agent failed (Requirement 6.6). */
    SKIPPED
}
