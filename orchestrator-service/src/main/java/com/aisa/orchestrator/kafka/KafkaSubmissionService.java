package com.aisa.orchestrator.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Service for submitting messages to Kafka with the following guarantees (Req 26.1, 26.5, 26.6):
 * <ul>
 *   <li>Acknowledge submission within 2 seconds</li>
 *   <li>Reject and retain data on Kafka unavailability (never silently lose data)</li>
 *   <li>Support re-queuing failed-instance work within 30 seconds</li>
 * </ul>
 */
@Service
public class KafkaSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(KafkaSubmissionService.class);

    /** Maximum time to wait for Kafka ack (Requirement 26.1). */
    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(2);

    /** Maximum time for re-queue operations (Requirement 26.6). */
    private static final Duration REQUEUE_TIMEOUT = Duration.ofSeconds(30);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaSubmissionService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Submit a message to a Kafka topic with guaranteed acknowledgment within 2 seconds.
     * <p>
     * On success, returns a {@link SubmissionResult} with status {@code ACKNOWLEDGED}.
     * On failure (Kafka unavailable, timeout, or any send error), throws
     * {@link KafkaSubmissionException} — the caller retains the original payload.
     *
     * @param topic   the Kafka topic name
     * @param key     the message key (used for partition routing; may be null)
     * @param payload the message payload (never null)
     * @return result indicating successful acknowledgment
     * @throws KafkaSubmissionException if Kafka is unavailable or ack times out
     */
    public SubmissionResult submit(String topic, String key, Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload must not be null");
        }

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(topic, key, payload);

        try {
            SendResult<String, Object> sendResult =
                    future.get(ACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            log.debug("Message acknowledged on topic={} partition={} offset={}",
                    topic,
                    sendResult.getRecordMetadata().partition(),
                    sendResult.getRecordMetadata().offset());

            return new SubmissionResult(
                    SubmissionResult.Status.ACKNOWLEDGED,
                    topic,
                    sendResult.getRecordMetadata().partition(),
                    sendResult.getRecordMetadata().offset()
            );
        } catch (TimeoutException e) {
            log.error("Kafka ack timeout for topic={} after {}ms", topic, ACK_TIMEOUT.toMillis());
            throw new KafkaSubmissionException(
                    "Kafka acknowledgment timed out within " + ACK_TIMEOUT.toSeconds() + "s",
                    topic, payload, e);
        } catch (ExecutionException e) {
            log.error("Kafka send failed for topic={}: {}", topic, e.getCause().getMessage());
            throw new KafkaSubmissionException(
                    "Kafka is unavailable or send failed: " + e.getCause().getMessage(),
                    topic, payload, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Kafka send interrupted for topic={}", topic);
            throw new KafkaSubmissionException(
                    "Kafka send was interrupted", topic, payload, e);
        }
    }

    /**
     * Re-queue work from a failed instance to Kafka within 30 seconds (Req 26.6).
     * <p>
     * Uses a longer timeout than normal submission to allow for transient recovery.
     *
     * @param topic   the Kafka topic name
     * @param key     the message key (used for partition routing; may be null)
     * @param payload the message payload representing the incomplete work
     * @return result indicating successful re-queue
     * @throws KafkaSubmissionException if re-queue fails within the 30-second window
     */
    public SubmissionResult requeue(String topic, String key, Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload must not be null");
        }

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(topic, key, payload);

        try {
            SendResult<String, Object> sendResult =
                    future.get(REQUEUE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);

            log.info("Work re-queued on topic={} partition={} offset={}",
                    topic,
                    sendResult.getRecordMetadata().partition(),
                    sendResult.getRecordMetadata().offset());

            return new SubmissionResult(
                    SubmissionResult.Status.REQUEUED,
                    topic,
                    sendResult.getRecordMetadata().partition(),
                    sendResult.getRecordMetadata().offset()
            );
        } catch (TimeoutException e) {
            log.error("Kafka re-queue timeout for topic={} after {}ms", topic, REQUEUE_TIMEOUT.toMillis());
            throw new KafkaSubmissionException(
                    "Kafka re-queue timed out within " + REQUEUE_TIMEOUT.toSeconds() + "s",
                    topic, payload, e);
        } catch (ExecutionException e) {
            log.error("Kafka re-queue failed for topic={}: {}", topic, e.getCause().getMessage());
            throw new KafkaSubmissionException(
                    "Kafka re-queue failed: " + e.getCause().getMessage(),
                    topic, payload, e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Kafka re-queue interrupted for topic={}", topic);
            throw new KafkaSubmissionException(
                    "Kafka re-queue was interrupted", topic, payload, e);
        }
    }
}
