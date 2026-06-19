package com.aisa.notification.messaging;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Unit tests for {@link NotificationEventConsumer}.
 *
 * <p>Validates that Kafka events are correctly forwarded to the appropriate
 * STOMP topics (Req 22.1, 22.2).
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventConsumerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationEventConsumer(messagingTemplate);
    }

    @Test
    @DisplayName("Agent progress event is forwarded to project progress and user notification STOMP topics")
    void agentProgress_forwardsToProjectAndUserTopics() {
        AgentProgressEvent event = new AgentProgressEvent(
                "proj-123", "user-456", "requirement-analyst",
                "STARTED", 1, "Starting requirement analysis",
                Instant.now(), "corr-789");

        consumer.onAgentProgress(event);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/project/proj-123/progress"), eq(event));
        verify(messagingTemplate).convertAndSend(
                eq("/topic/user/user-456/notifications"), eq(event));
    }

    @Test
    @DisplayName("Agent progress event with null projectId only fans out to user topic")
    void agentProgress_nullProjectId_onlyFansOutToUserTopic() {
        AgentProgressEvent event = new AgentProgressEvent(
                null, "user-456", "business-analyst",
                "SUCCESS", 1, "Business analysis complete",
                Instant.now(), null);

        consumer.onAgentProgress(event);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/user/user-456/notifications"), eq(event));
        verify(messagingTemplate, never()).convertAndSend(
                eq("/topic/project/null/progress"), eq(event));
    }

    @Test
    @DisplayName("Agent progress event with null userId only fans out to project topic")
    void agentProgress_nullUserId_onlyFansOutToProjectTopic() {
        AgentProgressEvent event = new AgentProgressEvent(
                "proj-123", null, "software-architect",
                "RETRY", 2, "Retrying...",
                Instant.now(), null);

        consumer.onAgentProgress(event);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/project/proj-123/progress"), eq(event));
        verify(messagingTemplate, never()).convertAndSend(
                eq("/topic/user/null/notifications"), eq(event));
    }

    @Test
    @DisplayName("Null agent progress event is discarded without STOMP interaction")
    void agentProgress_null_discardedSilently() {
        consumer.onAgentProgress(null);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("Project state change event is forwarded to project state and user notification STOMP topics")
    void projectStateChange_forwardsToProjectAndUserTopics() {
        ProjectStateChangeEvent event = new ProjectStateChangeEvent(
                "proj-123", "user-456", "DRAFT", "ANALYZING",
                Instant.now(), "corr-001");

        consumer.onProjectStateChange(event);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/project/proj-123/state"), eq(event));
        verify(messagingTemplate).convertAndSend(
                eq("/topic/user/user-456/notifications"), eq(event));
    }

    @Test
    @DisplayName("Project state change with null projectId only fans out to user topic")
    void projectStateChange_nullProjectId_onlyFansOutToUserTopic() {
        ProjectStateChangeEvent event = new ProjectStateChangeEvent(
                null, "user-456", "GENERATING", "IN_REVIEW",
                Instant.now(), null);

        consumer.onProjectStateChange(event);

        verify(messagingTemplate).convertAndSend(
                eq("/topic/user/user-456/notifications"), eq(event));
        verify(messagingTemplate, never()).convertAndSend(
                eq("/topic/project/null/state"), eq(event));
    }

    @Test
    @DisplayName("Null project state change event is discarded without STOMP interaction")
    void projectStateChange_null_discardedSilently() {
        consumer.onProjectStateChange(null);
        verifyNoInteractions(messagingTemplate);
    }
}
