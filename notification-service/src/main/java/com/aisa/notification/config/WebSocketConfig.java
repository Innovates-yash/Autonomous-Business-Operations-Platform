package com.aisa.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over WebSocket configuration for real-time event fan-out (Req 22, 29).
 *
 * <p>Topic destinations are scoped per project and per user:
 * <ul>
 *   <li>{@code /topic/project/{projectId}} — agent progress and state-change events for a project</li>
 *   <li>{@code /topic/user/{userId}} — personal notifications for a user</li>
 * </ul>
 *
 * <p>The {@link SubscriptionAuthInterceptor} enforces that a subscribing user
 * has access to the requested topic (Req 22.2).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final SubscriptionAuthInterceptor subscriptionAuthInterceptor;

    public WebSocketConfig(SubscriptionAuthInterceptor subscriptionAuthInterceptor) {
        this.subscriptionAuthInterceptor = subscriptionAuthInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable a simple in-memory broker for /topic destinations.
        registry.enableSimpleBroker("/topic");
        // Prefix for client-to-server messages (not used yet, but configured for future use).
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // WebSocket endpoint for STOMP connections (Req 22, 29).
        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Register subscription authorization interceptor (Req 22.2).
        registration.interceptors(subscriptionAuthInterceptor);
    }
}
