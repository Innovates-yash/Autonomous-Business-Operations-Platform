package com.aisa.audit.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aisa.audit.domain.AuditAction;
import com.aisa.audit.domain.AuditEvent;
import com.aisa.audit.recording.AuditRecordingException;
import com.aisa.audit.recording.AuditRecordingService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Unit tests for {@link AuditEventConsumer}: realizes audit-or-abort
 * (correctness Property 21 / Req 23.2). On successful recording nothing is
 * rejected; on exhausted retries a rejection signal gates the originating action.
 */
@ExtendWith(MockitoExtension.class)
class AuditEventConsumerTest {

    @Mock
    private AuditRecordingService recordingService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private AuditEventConsumer consumer;
    private AuditEventMessage message;

    @BeforeEach
    void setUp() {
        AuditMessagingProperties props = new AuditMessagingProperties("audit-events", "audit-rejections");
        consumer = new AuditEventConsumer(recordingService, kafkaTemplate, props);
        message = new AuditEventMessage(
                "action-42", "user-1", AuditAction.BLUEPRINT_APPROVAL, "blueprint-7",
                Instant.parse("2024-06-01T12:00:00.001Z"), "corr-9");
    }

    @Test
    void recordsAndPublishesNoRejectionOnSuccess() {
        when(recordingService.record(message)).thenReturn(
                new AuditEvent("user-1", AuditAction.BLUEPRINT_APPROVAL, "blueprint-7",
                        message.occurredAt(), "corr-9"));

        consumer.onAuditEvent(message);

        verify(recordingService).record(message);
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    void publishesRejectionKeyedByActionIdWhenRecordingExhausted() {
        when(recordingService.record(message))
                .thenThrow(new AuditRecordingException(4, "exhausted", new RuntimeException("db")));

        consumer.onAuditEvent(message);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq("audit-rejections"), eq("action-42"), payload.capture());

        assertThat(payload.getValue()).isInstanceOf(AuditRejection.class);
        AuditRejection rejection = (AuditRejection) payload.getValue();
        assertThat(rejection.actionId()).isEqualTo("action-42");
        assertThat(rejection.action()).isEqualTo(AuditAction.BLUEPRINT_APPROVAL);
        assertThat(rejection.targetId()).isEqualTo("blueprint-7");
        assertThat(rejection.attempts()).isEqualTo(4);
        assertThat(rejection.correlationId()).isEqualTo("corr-9");
    }

    @Test
    void ignoresNullMessage() {
        consumer.onAuditEvent(null);

        verify(recordingService, never()).record(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }
}
