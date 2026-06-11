package com.aisa.orchestrator.kafka;

/**
 * Result of a successful Kafka message submission.
 *
 * @param status    whether the message was acknowledged or re-queued
 * @param topic     the topic the message was sent to
 * @param partition the partition the message landed on
 * @param offset    the offset assigned by the broker
 */
public record SubmissionResult(
        Status status,
        String topic,
        int partition,
        long offset
) {

    public enum Status {
        /** Normal submission acknowledged within 2s (Req 26.1). */
        ACKNOWLEDGED,
        /** Re-queued work from a failed instance within 30s (Req 26.6). */
        REQUEUED
    }
}
