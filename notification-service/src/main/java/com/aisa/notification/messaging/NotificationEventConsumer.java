package com.aisa.notification.messaging;

import com.aisa.commons.correlation.CorrelationContext;
import com.aisa.commons.kafka.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer that receives agent-progress and project-state-change events
 * and forwards them to the appropriate STOMP topics (Req 22.1, 22.2).
 *
 * <p>Events are fanned out to two STOMP destinations:
 * <ul>
 *   <li>{@code /topic/project/{projectId}} — all subscribers watching that project</li>
 *   <li>{@code /topic/user/{userId}} — the user who owns/initiated the action</li>
 * </ul>
 *
 * <p>This ensures connected clients receive real-time updates within 2s of the
 * underlying event (Req 22.1).
 */
@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationEventConsumer(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Consumes agent progress events and forwards to STOMP topics.
     */
    @KafkaListener(
            topics = KafkaTopics.AGENT_PROGRESS,
            groupId = "${spring.kafka.consumer.group-id:notification-service}")
    public void onAgentProgress(AgentProgressEvent event) {
        if (event == null) {
            log.warn("Discarding null agent-progress event");
            return;
        }

        if (event.correlationId() != null) {
            CorrelationContext.set(event.correlationId());
        }

        try {
            log.debug("Agent progress: project={} agent={} status={} attempt={}",
                    event.projectId(), event.agentId(), event.status(), event.attempt());

            // Fan-out to project-scoped topic
            if (event.projectId() != null) {
                messagingTemplate.convertAndSend(
                        "/topic/project/" + event.projectId(), event);
            }

            // Fan-out to user-scoped topic
            if (event.userId() != null) {
                messagingTemplate.convertAndSend(
                        "/topic/user/" + event.userId(), event);
            }
        } finally {
            CorrelationContext.clear();
        }
    }

    /**
     * Consumes project state change events and forwards to STOMP topics.
     */
    @KafkaListener(
            topics = KafkaTopics.PROJECT_STATE_CHANGES,
            groupId = "${spring.kafka.consumer.group-id:notification-service}")
    public void onProjectStateChange(ProjectStateChangeEvent event) {
        if (event == null) {
            log.warn("Discarding null project-state-change event");
            return;
        }

        if (event.correlationId() != null) {
            CorrelationContext.set(event.correlationId());
        }

        try {
            log.debug("Project state change: project={} {} -> {}",
                    event.projectId(), event.previousState(), event.newState());

            // Fan-out to project-scoped topic
            if (event.projectId() != null) {
                messagingTemplate.convertAndSend(
                        "/topic/project/" + event.projectId(), event);
            }

            // Fan-out to user-scoped topic
            if (event.userId() != null) {
                messagingTemplate.convertAndSend(
                        "/topic/user/" + event.userId(), event);
            }
        } finally {
            CorrelationContext.clear();
        }
    }
}
