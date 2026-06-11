package com.aisa.orchestrator.domain;

/**
 * Status of a {@link GenerationRun} (Requirement 6).
 */
public enum GenerationRunStatus {

    /** Run has been created but not yet started. */
    PENDING,

    /** At least one agent invocation is in progress. */
    RUNNING,

    /** All agents completed successfully; Blueprint assembly has been signalled. */
    COMPLETED,

    /** One or more agents failed; dependent steps were halted (Requirement 6.6). */
    FAILED
}
