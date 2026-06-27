package com.aisa.orchestrator.kafka;

import com.aisa.orchestrator.domain.FailedWorkItem;
import com.aisa.orchestrator.domain.FailedWorkItemStatus;
import com.aisa.orchestrator.repository.FailedWorkItemRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FailedWorkRequeueScheduler}.
 * Validates: Requirements 26.5, 26.6 — re-queue failed-instance work ≤30s,
 * retain data on Kafka-unavailable.
 */
@ExtendWith(MockitoExtension.class)
class FailedWorkRequeueSchedulerTest {

    @Mock
    private FailedWorkItemRepository failedWorkItemRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Captor
    private ArgumentCaptor<FailedWorkItem> itemCaptor;

    private FailedWorkRequeueScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new FailedWorkRequeueScheduler(failedWorkItemRepository, kafkaTemplate);
    }

    @Test
    @DisplayName("requeueFailedWork: does nothing when no pending items exist")
    void requeueFailedWork_noItems_doesNothing() {
        when(failedWorkItemRepository.findByStatusOrderByCreatedAtAsc(FailedWorkItemStatus.PENDING))
                .thenReturn(Collections.emptyList());

        scheduler.requeueFailedWork();

        verify(kafkaTemplate, never()).send(any(String.class), any(), any());
    }

    @Test
    @DisplayName("requeueFailedWork: successfully re-queues pending item to Kafka (Req 26.6)")
    void requeueFailedWork_successfulRequeue_marksCompleted() {
        // Arrange
        FailedWorkItem item = new FailedWorkItem(
                "agent-tasks", "project-123", "{\"data\":\"test\"}", "java.lang.String", "original error");

        java.util.List<FailedWorkItemStatus> capturedStatuses = new java.util.ArrayList<>();
        when(failedWorkItemRepository.findByStatusOrderByCreatedAtAsc(FailedWorkItemStatus.PENDING))
                .thenReturn(List.of(item));
        when(failedWorkItemRepository.save(any(FailedWorkItem.class))).thenAnswer(inv -> {
            FailedWorkItem arg = inv.getArgument(0);
            capturedStatuses.add(arg.getStatus());
            return arg;
        });

        SendResult<String, Object> sendResult = createSendResult("agent-tasks", 0, 42L);
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(eq("agent-tasks"), eq("project-123"), any())).thenReturn(future);

        // Act
        scheduler.requeueFailedWork();

        // Assert — item should be saved twice: once as RETRYING, once as COMPLETED
        verify(failedWorkItemRepository, times(2)).save(itemCaptor.capture());
        List<FailedWorkItem> savedItems = itemCaptor.getAllValues();

        assertThat(capturedStatuses).containsExactly(FailedWorkItemStatus.RETRYING, FailedWorkItemStatus.COMPLETED);
        assertThat(savedItems.get(1).getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("requeueFailedWork: retains data and marks PENDING on transient failure (Req 26.5)")
    void requeueFailedWork_transientFailure_retainsDataAndMarksPending() {
        // Arrange
        FailedWorkItem item = new FailedWorkItem(
                "agent-tasks", "project-456", "{\"important\":\"data\"}", "java.lang.String", "broker down");

        java.util.List<FailedWorkItemStatus> capturedStatuses = new java.util.ArrayList<>();
        when(failedWorkItemRepository.findByStatusOrderByCreatedAtAsc(FailedWorkItemStatus.PENDING))
                .thenReturn(List.of(item));
        when(failedWorkItemRepository.save(any(FailedWorkItem.class))).thenAnswer(inv -> {
            FailedWorkItem arg = inv.getArgument(0);
            capturedStatuses.add(arg.getStatus());
            return arg;
        });

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new org.apache.kafka.common.errors.TimeoutException("Broker not available"));
        when(kafkaTemplate.send(eq("agent-tasks"), eq("project-456"), any())).thenReturn(future);

        // Act
        scheduler.requeueFailedWork();

        // Assert — item should be saved twice: once as RETRYING, once back to PENDING
        verify(failedWorkItemRepository, times(2)).save(itemCaptor.capture());
        List<FailedWorkItem> savedItems = itemCaptor.getAllValues();

        assertThat(capturedStatuses).containsExactly(FailedWorkItemStatus.RETRYING, FailedWorkItemStatus.PENDING);
        assertThat(savedItems.get(1).getRetryCount()).isEqualTo(1);
        // Data is retained in the entity — not lost
        assertThat(savedItems.get(1).getPayload()).isEqualTo("{\"important\":\"data\"}");
    }

    @Test
    @DisplayName("requeueFailedWork: marks EXHAUSTED when max retries reached (Req 26.6)")
    void requeueFailedWork_maxRetriesReached_marksExhausted() {
        // Arrange — item already at retry 4 of 5 max
        FailedWorkItem item = new FailedWorkItem(
                "agent-tasks", "project-789", "{\"data\":\"critical\"}", "java.lang.String", "repeated failure");
        // Simulate item that has been retried 4 times already
        for (int i = 0; i < 4; i++) {
            item.incrementRetryCount();
        }

        java.util.List<FailedWorkItemStatus> capturedStatuses = new java.util.ArrayList<>();
        when(failedWorkItemRepository.findByStatusOrderByCreatedAtAsc(FailedWorkItemStatus.PENDING))
                .thenReturn(List.of(item));
        when(failedWorkItemRepository.save(any(FailedWorkItem.class))).thenAnswer(inv -> {
            FailedWorkItem arg = inv.getArgument(0);
            capturedStatuses.add(arg.getStatus());
            return arg;
        });

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Still down"));
        when(kafkaTemplate.send(eq("agent-tasks"), eq("project-789"), any())).thenReturn(future);

        // Act
        scheduler.requeueFailedWork();

        // Assert — last save should mark EXHAUSTED (retry 5 of max 5)
        verify(failedWorkItemRepository, times(2)).save(itemCaptor.capture());
        List<FailedWorkItem> savedItems = itemCaptor.getAllValues();

        assertThat(capturedStatuses).containsExactly(FailedWorkItemStatus.RETRYING, FailedWorkItemStatus.EXHAUSTED);
        assertThat(savedItems.get(1).getRetryCount()).isEqualTo(5);
        // Data is still retained even when exhausted
        assertThat(savedItems.get(1).getPayload()).isEqualTo("{\"data\":\"critical\"}");
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
}
