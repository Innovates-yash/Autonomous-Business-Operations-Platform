package com.aisa.orchestrator.domain;

/**
 * Status of a {@link FailedWorkItem} in the re-queue lifecycle.
 */
public enum FailedWorkItemStatus {

    /** Awaiting re-queue attempt. */
    PENDING,

    /** Currently being re-submitted to Kafka. */
    RETRYING,

    /** Successfully re-queued to Kafka. */
    COMPLETED,

    /** All retry attempts exhausted; requires manual intervention. */
    EXHAUSTED
}
