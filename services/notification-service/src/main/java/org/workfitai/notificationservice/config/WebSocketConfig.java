package org.workfitai.notificationservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.workfitai.notificationservice.security.WebSocketAuthInterceptor;
import org.workfitai.notificationservice.security.WebSocketHandshakeInterceptor;

/**
 * WebSocket configuration for real-time notifications.
 * Uses STOMP over WebSocket for bi-directional communication.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor authInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple in-memory message broker
        // Prefix for messages FROM server TO client
        config.enableSimpleBroker("/topic", "/queue");

        // Prefix for messages FROM client TO server
        config.setApplicationDestinationPrefixes("/app");

        // User-specific messaging prefix
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // CORS handled by API Gateway - do not set CORS here to avoid duplicate headers
        registry.addEndpoint("/ws/notifications")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Add authentication interceptor for incoming messages
        registration.interceptors(authInterceptor);
    }
}
