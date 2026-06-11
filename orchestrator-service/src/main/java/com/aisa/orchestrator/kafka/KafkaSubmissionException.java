package com.aisa.orchestrator.kafka;

/**
 * Thrown when a Kafka submission fails — either the broker is unavailable,
 * the acknowledgment times out, or the send is interrupted.
 * <p>
 * Critically, this exception retains the original payload so the caller can
 * persist or resubmit it (Requirement 26.5: reject on Kafka-unavailable, retain data).
 */
public class KafkaSubmissionException extends RuntimeException {

    private final String topic;
    private final Object retainedPayload;

    public KafkaSubmissionException(String message, String topic, Object retainedPayload, Throwable cause) {
        super(message, cause);
        this.topic = topic;
        this.retainedPayload = retainedPayload;
    }

    /**
     * The topic the message was destined for.
     */
    public String getTopic() {
        return topic;
    }

    /**
     * The original payload that was not successfully delivered.
     * The caller is responsible for persisting or resubmitting this data.
     */
    public Object getRetainedPayload() {
        return retainedPayload;
    }
}
