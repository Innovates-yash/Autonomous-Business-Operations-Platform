package com.aisa.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

/**
 * Unit tests for {@link SubscriptionAuthInterceptor}.
 *
 * <p>Validates that subscription authorization logic works correctly (Req 22.2):
 * <ul>
 *   <li>Users can subscribe to their own user notification topic</li>
 *   <li>Users cannot subscribe to another user's notification topic</li>
 *   <li>Authenticated users can subscribe to project-scoped topics</li>
 *   <li>Unauthenticated users (no X-User-Id) are rejected</li>
 * </ul>
 */
class SubscriptionAuthInterceptorTest {

    private SubscriptionAuthInterceptor interceptor;
    private MessageChannel mockChannel;

    @BeforeEach
    void setUp() {
        interceptor = new SubscriptionAuthInterceptor();
        mockChannel = (message, timeout) -> true;
    }

    @Test
    @DisplayName("CONNECT stores X-User-Id in session attributes")
    void connect_storesUserIdInSession() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader(SubscriptionAuthInterceptor.USER_ID_HEADER, "user-123");
        Map<String, Object> sessionAttrs = new HashMap<>();
        accessor.setSessionAttributes(sessionAttrs);
        accessor.setSessionId("session-1");

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, mockChannel);

        assertThat(result).isNotNull();
        assertThat(sessionAttrs.get(SubscriptionAuthInterceptor.SESSION_USER_ID_ATTR))
                .isEqualTo("user-123");
    }

    @Test
    @DisplayName("User can subscribe to their own notification topic")
    void subscribe_ownUserTopic_allowed() {
        Message<?> result = sendSubscribe("user-123", "/topic/user/user-123/notifications");

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("User cannot subscribe to another user's notification topic")
    void subscribe_otherUserTopic_rejected() {
        Message<?> result = sendSubscribe("user-123", "/topic/user/user-999/notifications");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Authenticated user can subscribe to project progress topic")
    void subscribe_projectProgressTopic_allowed() {
        Message<?> result = sendSubscribe("user-123", "/topic/project/proj-456/progress");

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Authenticated user can subscribe to project state topic")
    void subscribe_projectStateTopic_allowed() {
        Message<?> result = sendSubscribe("user-123", "/topic/project/proj-456/state");

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Unauthenticated subscription to project topic is rejected")
    void subscribe_projectTopic_noUserId_rejected() {
        Message<?> result = sendSubscribeWithoutUserId("/topic/project/proj-456/progress");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Unauthenticated subscription to user topic is rejected")
    void subscribe_userTopic_noUserId_rejected() {
        Message<?> result = sendSubscribeWithoutUserId("/topic/user/user-123/notifications");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Subscription to unknown topic pattern is rejected")
    void subscribe_unknownTopic_rejected() {
        Message<?> result = sendSubscribe("user-123", "/topic/unknown/path");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Subscription with null destination is rejected")
    void subscribe_nullDestination_rejected() {
        Message<?> result = sendSubscribe("user-123", null);

        assertThat(result).isNull();
    }

    /**
     * Helper to simulate a SUBSCRIBE message with a user authenticated via session.
     */
    private Message<?> sendSubscribe(String userId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        // Simulate session attributes populated during CONNECT
        Map<String, Object> sessionAttrs = new HashMap<>();
        sessionAttrs.put(SubscriptionAuthInterceptor.SESSION_USER_ID_ATTR, userId);
        accessor.setSessionAttributes(sessionAttrs);
        accessor.setSessionId("session-1");
        accessor.setSubscriptionId("sub-1");

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return interceptor.preSend(message, mockChannel);
    }

    /**
     * Helper to simulate a SUBSCRIBE message without any user identification.
     */
    private Message<?> sendSubscribeWithoutUserId(String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        Map<String, Object> sessionAttrs = new HashMap<>();
        accessor.setSessionAttributes(sessionAttrs);
        accessor.setSessionId("session-1");
        accessor.setSubscriptionId("sub-1");

        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        return interceptor.preSend(message, mockChannel);
    }
}
