package com.aisa.notification.config;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Channel interceptor that enforces subscription authorization (Req 22.2).
 *
 * <p>On CONNECT, extracts the {@code X-User-Id} header from the STOMP connect
 * headers and stores it in the session attributes for the duration of the connection.
 *
 * <p>On SUBSCRIBE, verifies the subscribing user has access to the target topic:
 * <ul>
 *   <li>{@code /topic/project/{projectId}} — allowed if user is a member/owner of the project.
 *       For now, we verify the user is authenticated (has X-User-Id). Full project-membership
 *       check can be integrated via a project-service call in production.</li>
 *   <li>{@code /topic/user/{userId}} — allowed only if the subscribing user's ID matches {userId}.</li>
 * </ul>
 *
 * <p>If authorization fails, the message is rejected (returns null) preventing the subscription.
 */
@Component
public class SubscriptionAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionAuthInterceptor.class);

    static final String USER_ID_HEADER = "X-User-Id";
    static final String SESSION_USER_ID_ATTR = "userId";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }

        switch (command) {
            case CONNECT -> handleConnect(accessor);
            case SUBSCRIBE -> {
                if (!handleSubscribe(accessor)) {
                    return null; // Reject unauthorized subscription
                }
            }
            default -> { /* No-op for other commands */ }
        }

        return message;
    }

    /**
     * Extract X-User-Id from STOMP connect headers and store in session.
     */
    private void handleConnect(StompHeaderAccessor accessor) {
        String userId = extractUserId(accessor);
        if (userId != null) {
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes != null) {
                sessionAttributes.put(SESSION_USER_ID_ATTR, userId);
            }
            log.debug("STOMP CONNECT: userId={}", userId);
        } else {
            log.warn("STOMP CONNECT without X-User-Id header");
        }
    }

    /**
     * Verify the user is authorized to subscribe to the requested topic.
     *
     * @return true if subscription is allowed, false to reject
     */
    private boolean handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return false;
        }

        String userId = getSessionUserId(accessor);
        if (userId == null) {
            log.warn("Subscription denied: no authenticated user for destination={}", destination);
            return false;
        }

        // /topic/user/{userId} — only the user themselves can subscribe
        if (destination.startsWith("/topic/user/")) {
            String targetUserId = destination.substring("/topic/user/".length());
            if (!userId.equals(targetUserId)) {
                log.warn("Subscription denied: user={} tried to subscribe to /topic/user/{}", userId, targetUserId);
                return false;
            }
            return true;
        }

        // /topic/project/{projectId} — user must be authenticated (project access check)
        if (destination.startsWith("/topic/project/")) {
            // The user is authenticated (has X-User-Id). In a full implementation,
            // we would verify project membership via project-service. For now,
            // being authenticated suffices for the project-scoped subscription.
            log.debug("Subscription allowed: user={} to destination={}", userId, destination);
            return true;
        }

        // Unknown topic pattern — deny by default
        log.warn("Subscription denied: unknown destination pattern={}", destination);
        return false;
    }

    /**
     * Get user ID from session attributes (set during CONNECT).
     */
    private String getSessionUserId(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes != null) {
            Object userId = sessionAttributes.get(SESSION_USER_ID_ATTR);
            if (userId instanceof String s) {
                return s;
            }
        }
        // Fallback: check the native STOMP headers on this frame
        return extractUserId(accessor);
    }

    /**
     * Extract X-User-Id from STOMP native headers.
     */
    private String extractUserId(StompHeaderAccessor accessor) {
        List<String> headerValues = accessor.getNativeHeader(USER_ID_HEADER);
        if (headerValues != null && !headerValues.isEmpty()) {
            String value = headerValues.getFirst();
            if (value != null && !value.isBlank()) {
                return value.strip();
            }
        }
        return null;
    }
}
