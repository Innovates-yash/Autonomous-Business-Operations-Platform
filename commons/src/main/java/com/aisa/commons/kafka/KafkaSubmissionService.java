package com.aisa.commons.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Shared service that wraps {@link KafkaTemplate} with platform-mandated semantics:
 * <ul>
 *   <li>Synchronous send with ≤2 second acknowledgement timeout (Requirement 26.5)</li>
 *   <li>Throws {@link KafkaUnavailableException} on broker unreachability, preserving
 *       the original payload for caller retry/persistence (Requirement 26.5)</li>
 *   <li>Works with consumer group rebalance within 30s via session.timeout.ms
 *       configured per-service (Requirement 26.6)</li>
 * </ul>
 *
 * <p>This service is NOT an auto-configuration bean — services that need it should
 * declare it as a {@code @Bean} or inject it via their own configuration class alongside
 * a {@link KafkaTemplate}. This avoids forcing a KafkaTemplate dependency on services
 * that only consume.
 *
 * @see KafkaSubmissionAutoConfiguration for auto-configuration that creates this bean
 */
public class KafkaSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(KafkaSubmissionService.class);

    /** Maximum time to wait for Kafka broker acknowledgement (Requirement 26.5). */
    private static final Duration ACK_TIMEOUT = Duration.ofSeconds(2);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaSubmissionService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Sends a message to the specified Kafka topic with synchronous acknowledgement.
     *
     * @param topic   the target topic (use {@link KafkaTopics} constants)
     * @param key     the message key for partitioning (nullable)
     * @param payload the message value
     * @return the send result on success
     * @throws KafkaUnavailableException if broker is unreachable or ack not received within 2s
     */
    public SendResult<String, Object> send(String topic, String key, Object payload) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, payload);
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(record);

        try {
            SendResult<String, Object> result = future.get(ACK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            log.debug("Kafka message sent to topic={} partition={} offset={}",
                    topic, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            return result;
        } catch (TimeoutException e) {
            log.error("Kafka ack timeout ({}ms) for topic={}", ACK_TIMEOUT.toMillis(), topic, e);
            throw new KafkaUnavailableException(topic, payload, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Kafka send failed for topic={}: {}", topic, cause.getMessage(), cause);
            throw new KafkaUnavailableException(topic, payload, cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Kafka send interrupted for topic={}", topic, e);
            throw new KafkaUnavailableException(topic, payload, e);
        }
    }

    /**
     * Convenience overload that sends without an explicit key (round-robin partitioning).
     *
     * @param topic   the target topic
     * @param payload the message value
     * @return the send result on success
     * @throws KafkaUnavailableException if broker is unreachable or ack not received within 2s
     */
    public SendResult<String, Object> send(String topic, Object payload) {
        return send(topic, null, payload);
    }
}
