package org.workfitai.apigateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WebSocket Routes Configuration for API Gateway.
 * <p>
 * Spring Cloud Gateway requires explicit routes for WebSocket proxying.
 * Consul service discovery (locator.enabled=true) does NOT automatically
 * handle WebSocket upgrade requests - we must define them manually.
 * </p>
 */
@Configuration
@Slf4j
public class WebSocketRoutesConfig {

    @Bean
    public RouteLocator webSocketRoutes(RouteLocatorBuilder builder) {
        log.info("🔌 Configuring WebSocket routes for notification-service");

        return builder.routes()
                // Route 1: WebSocket endpoint (SockJS + all fallback transports)
                // Order: -1 = highest priority (evaluated before Discovery Locator routes)
                .route("notification-websocket", r -> r
                        .order(-1) // ⚠️ CRITICAL: Must be evaluated BEFORE auto-discovery routes
                        .path("/notification/ws/**")
                        .filters(f -> f
                                .stripPrefix(1) // Remove /notification prefix → /ws/**
                                .removeRequestHeader("Sec-WebSocket-Extensions")) // Clean WebSocket headers
                        .uri("lb:ws://notification")) // Use ws:// for WebSocket protocol

                // Route 2: Notification REST API (standard HTTP)
                .route("notification-api", r -> r
                        .path("/notification/**")
                        .filters(f -> f.stripPrefix(1))
                        .uri("lb://notification"))

                .build();
    }
}
