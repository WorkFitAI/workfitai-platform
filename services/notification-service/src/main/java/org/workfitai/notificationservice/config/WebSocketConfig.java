package org.workfitai.notificationservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
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
        // Enable simple in-memory message broker with heartbeat
        // Heartbeat: client sends every 10s, server sends every 10s
        config.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] { 10000, 10000 })
                .setTaskScheduler(taskScheduler());

        // Prefix for messages FROM client TO server
        config.setApplicationDestinationPrefixes("/app");

        // User-specific messaging prefix
        config.setUserDestinationPrefix("/user");
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10); // 10 threads for heartbeat/scheduling
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // CORS handled by API Gateway - do not set CORS here to avoid duplicate headers
        registry.addEndpoint("/ws/notifications")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Thread pool for incoming messages from clients
        registration
                .interceptors(authInterceptor)
                .taskExecutor()
                .corePoolSize(10) // Min threads
                .maxPoolSize(50) // Max threads
                .queueCapacity(100); // Queue size before rejection
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // Thread pool for outgoing messages to clients
        registration
                .taskExecutor()
                .corePoolSize(10)
                .maxPoolSize(50)
                .queueCapacity(100);
    }
}
