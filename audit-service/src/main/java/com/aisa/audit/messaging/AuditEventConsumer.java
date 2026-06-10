package com.aisa.audit.messaging;

import com.aisa.audit.domain.AuditEvent;
import com.aisa.audit.recording.AuditRecordingException;
import com.aisa.audit.recording.AuditRecordingService;
import com.aisa.commons.correlation.CorrelationContext;
import com.aisa.commons.error.ErrorCodes;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Consumes audit-event requests from the {@code audit-events} topic and records
 * them durably (Req 23.1), retrying transient failures up to the configured
 * limit (Req 23.2).
 *
 * <p>On exhaustion of all retries the consumer does <em>not</em> throw back into
 * the container (which would cause unbounded redelivery). Instead it publishes an
 * {@link AuditRejection} to the {@code audit-rejections} topic, keyed by the
 * originating action id, so the originating service rejects/rolls back its action
 * while preserving prior system state. This realizes audit-or-abort (correctness
 * Property 21): a security-relevant action is either durably audited or rejected.
 */
@Component
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final AuditRecordingService recordingService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditMessagingProperties messagingProperties;

    public AuditEventConsumer(AuditRecordingService recordingService,
                              KafkaTemplate<String, Object> kafkaTemplate,
                              AuditMessagingProperties messagingProperties) {
        this.recordingService = recordingService;
        this.kafkaTemplate = kafkaTemplate;
        this.messagingProperties = messagingProperties;
    }

    @KafkaListener(
            topics = "${audit.messaging.events-topic:audit-events}",
            groupId = "${spring.kafka.consumer.group-id:audit-service}")
    public void onAuditEvent(AuditEventMessage message) {
        if (message == null) {
            log.warn("Discarding null audit-event message");
            return;
        }
        if (message.correlationId() != null) {
            CorrelationContext.set(message.correlationId());
        }
        try {
            AuditEvent persisted = recordingService.record(message);
            log.debug("Recorded audit event id={} action={} target={}",
                    persisted.getId(), persisted.getAction(), persisted.getTargetId());
        } catch (AuditRecordingException ex) {
            log.error("Audit recording exhausted retries for action={} target={}; "
                            + "rejecting originating action actionId={}",
                    message.action(), message.targetId(), message.actionId(), ex);
            publishRejection(message, ex);
        } finally {
            CorrelationContext.clear();
        }
    }

    private void publishRejection(AuditEventMessage message, AuditRecordingException ex) {
        AuditRejection rejection = new AuditRejection(
                message.actionId(),
                message.userId(),
                message.action(),
                message.targetId(),
                ErrorCodes.INTERNAL_ERROR,
                "Audit event could not be recorded; originating action rejected",
                ex.getAttempts(),
                Instant.now(),
                message.correlationId());
        // Key by the originating action id so the originating service can correlate
        // the rejection to the exact action it must undo (Req 23.2).
        kafkaTemplate.send(messagingProperties.rejectionsTopic(), message.actionId(), rejection);
    }
}
