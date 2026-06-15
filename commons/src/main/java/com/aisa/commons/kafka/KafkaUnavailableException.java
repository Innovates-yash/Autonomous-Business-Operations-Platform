package com.aisa.commons.kafka;

/**
 * Thrown when a Kafka send operation cannot complete because the broker is unreachable
 * or did not acknowledge within the configured timeout.
 *
 * <p>The exception preserves the original payload so that callers can persist it for
 * later retry without data loss (Requirement 26.5).
 */
public class KafkaUnavailableException extends RuntimeException {

    private final String topic;
    private final Object payload;

    public KafkaUnavailableException(String topic, Object payload, Throwable cause) {
        super("Kafka unavailable for topic [" + topic + "]: " + cause.getMessage(), cause);
        this.topic = topic;
        this.payload = payload;
    }

    /** The topic the message was destined for. */
    public String getTopic() {
        return topic;
    }

    /** The original, unmodified payload that was not delivered. */
    public Object getPayload() {
        return payload;
    }
}
