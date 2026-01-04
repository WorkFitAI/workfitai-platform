package org.workfitai.notificationservice.config;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.workfitai.notificationservice.security.WebSocketAuthInterceptor;

import java.util.Map;

/**
 * WebSocket configuration for real-time notifications.
 * Uses STOMP over WebSocket for bi-directional communication.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
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
        // SockJS disabled - using native WebSocket only (Gateway does not support
        // SockJS)
        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns("*") // Allow all origins, CORS validated at Gateway
                .addInterceptors(new HandshakeInterceptor() {
                    @Override
                    public boolean beforeHandshake(
                            ServerHttpRequest request,
                            ServerHttpResponse response,
                            WebSocketHandler wsHandler,
                            Map<String, Object> attributes) throws Exception {
                        // Extract token from query parameter
                        if (request instanceof ServletServerHttpRequest) {
                            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request)
                                    .getServletRequest();
                            String token = servletRequest.getParameter("token");
                            if (token != null) {
                                attributes.put("token", token);
                                log.debug("[WebSocket] Token extracted from query param during handshake");
                            }
                        }
                        return true;
                    }

                    @Override
                    public void afterHandshake(
                            ServerHttpRequest request,
                            ServerHttpResponse response,
                            WebSocketHandler wsHandler,
                            Exception exception) {
                    }
                });
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
