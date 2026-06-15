package com.aisa.commons.kafka;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KafkaSubmissionService}.
 *
 * <p>Validates:
 * <ul>
 *   <li>Submission acknowledged within 2s (Requirement 26.5)</li>
 *   <li>Kafka-unavailable preserves payload (Requirement 26.5)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class KafkaSubmissionServiceTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaSubmissionService service;

    @BeforeEach
    void setUp() {
        service = new KafkaSubmissionService(kafkaTemplate);
    }

    @Test
    @DisplayName("send returns SendResult when ack received within 2s")
    void send_success_returnsResult() {
        // Arrange
        String topic = KafkaTopics.AGENT_TASKS;
        String key = "task-123";
        Object payload = "test-payload";

        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(topic, 0), 0L, 0, 0L, 0, 0);
        SendResult<String, Object> expectedResult = new SendResult<>(
                new ProducerRecord<>(topic, key, payload), metadata);

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(expectedResult);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Act
        SendResult<String, Object> result = service.send(topic, key, payload);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getRecordMetadata().topic()).isEqualTo(topic);
        assertThat(result.getRecordMetadata().partition()).isEqualTo(0);
    }

    @Test
    @DisplayName("send throws KafkaUnavailableException on timeout, preserving payload")
    void send_timeout_throwsKafkaUnavailableWithPayload() {
        // Arrange
        String topic = KafkaTopics.AGENT_PROGRESS;
        String key = "progress-1";
        Object payload = new TestPayload("important-data");

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        // Simulate timeout: the future never completes
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Cancel the future with a timeout after a short delay to simulate timeout
        // The service uses 2s timeout, but we'll make the future fail immediately
        future.completeExceptionally(new TimeoutException("Broker unreachable"));

        // Act & Assert
        assertThatThrownBy(() -> service.send(topic, key, payload))
                .isInstanceOf(KafkaUnavailableException.class)
                .satisfies(ex -> {
                    KafkaUnavailableException kafkaEx = (KafkaUnavailableException) ex;
                    assertThat(kafkaEx.getTopic()).isEqualTo(topic);
                    assertThat(kafkaEx.getPayload()).isEqualTo(payload);
                });
    }

    @Test
    @DisplayName("send throws KafkaUnavailableException on broker failure, preserving payload")
    void send_brokerFailure_throwsKafkaUnavailableWithPayload() {
        // Arrange
        String topic = KafkaTopics.AUDIT_EVENTS;
        Object payload = new TestPayload("audit-data");

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new org.apache.kafka.common.errors.TimeoutException("Broker not available"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Act & Assert
        assertThatThrownBy(() -> service.send(topic, payload))
                .isInstanceOf(KafkaUnavailableException.class)
                .satisfies(ex -> {
                    KafkaUnavailableException kafkaEx = (KafkaUnavailableException) ex;
                    assertThat(kafkaEx.getTopic()).isEqualTo(topic);
                    assertThat(kafkaEx.getPayload()).isEqualTo(payload);
                    assertThat(kafkaEx.getMessage()).contains("Kafka unavailable");
                });
    }

    @Test
    @DisplayName("send without key uses null key (round-robin partitioning)")
    void send_withoutKey_usesNullKey() {
        // Arrange
        String topic = KafkaTopics.PROJECT_STATE_CHANGES;
        Object payload = "state-change-event";

        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(topic, 1), 5L, 0, 0L, 0, 0);
        SendResult<String, Object> expectedResult = new SendResult<>(
                new ProducerRecord<>(topic, null, payload), metadata);

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(expectedResult);
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Act
        SendResult<String, Object> result = service.send(topic, payload);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getRecordMetadata().offset()).isEqualTo(5L);
    }

    @Test
    @DisplayName("KafkaUnavailableException preserves original payload type")
    void kafkaUnavailableException_preservesPayloadType() {
        // Arrange
        TestPayload originalPayload = new TestPayload("critical-event");
        String topic = KafkaTopics.AGENT_TASKS;

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Connection refused"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        // Act & Assert
        assertThatThrownBy(() -> service.send(topic, "key", originalPayload))
                .isInstanceOf(KafkaUnavailableException.class)
                .satisfies(ex -> {
                    KafkaUnavailableException kafkaEx = (KafkaUnavailableException) ex;
                    assertThat(kafkaEx.getPayload())
                            .isInstanceOf(TestPayload.class)
                            .isEqualTo(originalPayload);
                });
    }

    /** Simple record for testing payload preservation. */
    private record TestPayload(String data) {}
}
