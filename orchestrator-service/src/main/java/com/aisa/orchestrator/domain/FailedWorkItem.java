package com.aisa.orchestrator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Persists work items that failed to be submitted to Kafka so they can be
 * re-queued by the {@link com.aisa.orchestrator.kafka.FailedWorkRequeueScheduler}
 * within 30 seconds (Requirement 26.5, 26.6).
 *
 * <p>When Kafka is unavailable, the {@link com.aisa.orchestrator.kafka.KafkaSubmissionService}
 * stores the retained payload here instead of silently dropping it. The scheduler
 * picks up PENDING items and re-attempts submission.
 */
@Entity
@Table(name = "failed_work_item")
public class FailedWorkItem {

    @Id
    @GeneratedValue
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /** The Kafka topic the message was destined for. */
    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    /** The message key for partition routing (may be null). */
    @Column(name = "message_key", length = 255)
    private String messageKey;

    /** JSON-serialized payload retained from the failed submission. */
    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    /** The fully-qualified class name of the payload for deserialization. */
    @Column(name = "payload_type", nullable = false, length = 512)
    private String payloadType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private FailedWorkItemStatus status = FailedWorkItemStatus.PENDING;

    /** Number of re-queue attempts made so far. */
    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    /** Maximum retries before marking as EXHAUSTED. */
    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 5;

    /** The error message from the original failure. */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Timestamp of the last re-queue attempt. */
    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    protected FailedWorkItem() {
        // Required by JPA.
    }

    public FailedWorkItem(String topic, String messageKey, String payload, String payloadType, String errorMessage) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.payloadType = payloadType;
        this.errorMessage = errorMessage;
        this.status = FailedWorkItemStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // --- Getters and Setters ---

    public UUID getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public String getPayload() {
        return payload;
    }

    public String getPayloadType() {
        return payloadType;
    }

    public FailedWorkItemStatus getStatus() {
        return status;
    }

    public void setStatus(FailedWorkItemStatus status) {
        this.status = status;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(Instant lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }
}
