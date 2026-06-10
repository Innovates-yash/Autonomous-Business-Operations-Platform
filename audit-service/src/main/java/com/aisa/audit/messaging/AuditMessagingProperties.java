package com.aisa.audit.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Topic names for audit ingestion and rejection signalling (Req 23.1, 23.2).
 *
 * @param eventsTopic     inbound topic carrying {@link AuditEventMessage}s from
 *                        originating services
 * @param rejectionsTopic outbound topic carrying {@link AuditRejection}s when an
 *                        audit event cannot be recorded and the originating
 *                        action must be rejected
 */
@ConfigurationProperties(prefix = "audit.messaging")
public record AuditMessagingProperties(
        String eventsTopic,
        String rejectionsTopic) {

    public AuditMessagingProperties {
        if (eventsTopic == null || eventsTopic.isBlank()) {
            eventsTopic = "audit-events";
        }
        if (rejectionsTopic == null || rejectionsTopic.isBlank()) {
            rejectionsTopic = "audit-rejections";
        }
    }
}
