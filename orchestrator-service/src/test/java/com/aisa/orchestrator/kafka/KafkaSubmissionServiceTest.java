package com.aisa.orchestrator.kafka;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KafkaSubmissionService}.
 * Validates: Requirements 26.1, 26.5, 26.6.
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
    @DisplayName("submit: acknowledges within 2s when Kafka is available (Req 26.1)")
    void submit_acknowledgesWithinTimeout() {
        // Arrange
        String topic = "agent-tasks";
        String key = "project-123";
        Object payload = "test-payload";

        SendResult<String, Object> sendResult = createSendResult(topic, 2, 42L);
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(eq(topic), eq(key), eq(payload))).thenReturn(future);

        // Act
        SubmissionResult result = service.submit(topic, key, payload);

        // Assert
        assertThat(result.status()).isEqualTo(SubmissionResult.Status.ACKNOWLEDGED);
        assertThat(result.topic()).isEqualTo(topic);
        assertThat(result.partition()).isEqualTo(2);
        assertThat(result.offset()).isEqualTo(42L);
    }

    @Test
    @DisplayName("submit: rejects and retains payload when Kafka is unavailable (Req 26.5)")
    void submit_rejectsOnKafkaUnavailable_retainsData() {
        // Arrange
        String topic = "agent-tasks";
        String key = "project-456";
        Object payload = new TestPayload("important-data", 99);

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new org.apache.kafka.common.errors.TimeoutException("Broker not available"));
        when(kafkaTemplate.send(eq(topic), eq(key), eq(payload))).thenReturn(future);

        // Act & Assert
        assertThatThrownBy(() -> service.submit(topic, key, payload))
                .isInstanceOf(KafkaSubmissionException.class)
                .satisfies(thrown -> {
                    KafkaSubmissionException ex = (KafkaSubmissionException) thrown;
                    assertThat(ex.getTopic()).isEqualTo(topic);
                    // Verify original payload is retained (Req 26.5)
                    assertThat(ex.getRetainedPayload()).isSameAs(payload);
                    assertThat(ex.getMessage()).contains("unavailable");
                });
    }

    @Test
    @DisplayName("submit: rejects and retains payload when ack times out (Req 26.1, 26.5)")
    void submit_rejectsOnAckTimeout_retainsData() {
        // Arrange
        String topic = "agent-progress";
        String key = "run-789";
        Object payload = "timeout-payload";

        // A future that never completes simulates a timeout
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(eq(topic), eq(key), eq(payload))).thenReturn(future);

        // Act & Assert — submit will time out after 2s
        assertThatThrownBy(() -> service.submit(topic, key, payload))
                .isInstanceOf(KafkaSubmissionException.class)
                .satisfies(thrown -> {
                    KafkaSubmissionException ex = (KafkaSubmissionException) thrown;
                    assertThat(ex.getTopic()).isEqualTo(topic);
                    assertThat(ex.getRetainedPayload()).isSameAs(payload);
                    assertThat(ex.getMessage()).contains("timed out");
                });
    }

    @Test
    @DisplayName("requeue: re-queues failed-instance work within 30s (Req 26.6)")
    void requeue_successfullyRequeuesWork() {
        // Arrange
        String topic = "agent-tasks";
        String key = "project-requeue";
        Object payload = "incomplete-work";

        SendResult<String, Object> sendResult = createSendResult(topic, 0, 100L);
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(eq(topic), eq(key), eq(payload))).thenReturn(future);

        // Act
        SubmissionResult result = service.requeue(topic, key, payload);

        // Assert
        assertThat(result.status()).isEqualTo(SubmissionResult.Status.REQUEUED);
        assertThat(result.topic()).isEqualTo(topic);
        assertThat(result.partition()).isEqualTo(0);
        assertThat(result.offset()).isEqualTo(100L);
    }

    @Test
    @DisplayName("requeue: rejects and retains payload when Kafka is unavailable (Req 26.5, 26.6)")
    void requeue_rejectsOnKafkaUnavailable_retainsData() {
        // Arrange
        String topic = "agent-tasks";
        String key = "project-fail";
        Object payload = new TestPayload("requeue-data", 42);

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(
                new org.apache.kafka.common.errors.NotLeaderOrFollowerException("No leader"));
        when(kafkaTemplate.send(eq(topic), eq(key), eq(payload))).thenReturn(future);

        // Act & Assert
        assertThatThrownBy(() -> service.requeue(topic, key, payload))
                .isInstanceOf(KafkaSubmissionException.class)
                .satisfies(thrown -> {
                    KafkaSubmissionException ex = (KafkaSubmissionException) thrown;
                    assertThat(ex.getTopic()).isEqualTo(topic);
                    assertThat(ex.getRetainedPayload()).isSameAs(payload);
                    assertThat(ex.getMessage()).contains("re-queue failed");
                });
    }

    @Test
    @DisplayName("submit: rejects null payload with IllegalArgumentException")
    void submit_rejectsNullPayload() {
        assertThatThrownBy(() -> service.submit("agent-tasks", "key", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payload must not be null");
    }

    @Test
    @DisplayName("requeue: rejects null payload with IllegalArgumentException")
    void requeue_rejectsNullPayload() {
        assertThatThrownBy(() -> service.requeue("agent-tasks", "key", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payload must not be null");
    }

    // --- Helpers ---

    @SuppressWarnings("unchecked")
    private SendResult<String, Object> createSendResult(String topic, int partition, long offset) {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition(topic, partition),
                offset, 0, System.currentTimeMillis(), 0, 0);
        ProducerRecord<String, Object> producerRecord = new ProducerRecord<>(topic, "key", "value");
        return new SendResult<>(producerRecord, metadata);
    }

    /**
     * Simple test payload to verify object identity is preserved through exception.
     */
    record TestPayload(String data, int sequence) {
    }
}
