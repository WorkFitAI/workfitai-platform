package org.workfitai.apigateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * CORS Configuration for API Gateway
 *
 * Features:
 * - Env-driven origin whitelisting via APP_ENV (dev | prod) + ALLOWED_ORIGINS
 * - Separate WebSocket CORS configuration for SockJS compatibility
 * - Production validation (prod MUST set explicit, non-wildcard ALLOWED_ORIGINS)
 * - Prevents 403 Invalid CORS errors for WebSocket connections
 */
@Configuration
@Slf4j
public class CorsGlobalConfig {

    @Value("${app.env:dev}")
    private String appEnv;

    @Value("${app.cors.allowed-origins:http://localhost:3000}")
    private String apiOrigins;

    @Value("${app.cors.websocket-origins:http://localhost:3000}")
    private String wsOrigins;

    @Value("${app.cors.max-age:3600}")
    private long maxAge;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}")
    private String allowedMethods;

    @Value("${app.cors.exposed-headers:X-Total-Count,X-Page-Number,X-Page-Size}")
    private String exposedHeaders;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        boolean prod = "prod".equalsIgnoreCase(appEnv) || "production".equalsIgnoreCase(appEnv);

        log.info("🌐 Configuring CORS for app.env: {}", appEnv);

        // Fail fast in prod on empty/wildcard origins (credentials are enabled).
        if (prod && (apiOrigins.isBlank() || apiOrigins.contains("*"))) {
            throw new IllegalStateException(
                    "⛔ PRODUCTION REQUIRES EXPLICIT ALLOWED_ORIGINS! " +
                            "Set environment variable: ALLOWED_ORIGINS=https://yourdomain.com");
        }

        List<String> allowedOriginList = Arrays.stream(apiOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        List<String> wsOriginList = Arrays.stream(wsOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        // Standard API CORS configuration
        CorsConfiguration apiConfig = new CorsConfiguration();
        apiConfig.setAllowedOrigins(allowedOriginList);
        apiConfig.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));
        apiConfig.setAllowedHeaders(List.of("*"));
        apiConfig.setExposedHeaders(Arrays.asList(exposedHeaders.split(",")));
        apiConfig.setAllowCredentials(true);
        apiConfig.setMaxAge(Duration.ofSeconds(maxAge));

        // WebSocket CORS configuration (more permissive for SockJS)
        CorsConfiguration wsConfig = new CorsConfiguration();
        wsConfig.setAllowedOrigins(wsOriginList);
        wsConfig.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        wsConfig.setAllowedHeaders(List.of("*"));
        wsConfig.setAllowCredentials(true);
        wsConfig.setMaxAge(Duration.ofSeconds(maxAge));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        // Register WebSocket paths FIRST (more specific)
        source.registerCorsConfiguration("/notification/ws/**", wsConfig);

        // Register all other paths
        source.registerCorsConfiguration("/**", apiConfig);

        log.info("✅ CORS configured - API Origins: {}", allowedOriginList);
        log.info("✅ CORS configured - WebSocket Origins: {}", wsOriginList);
        log.info("✅ CORS Max Age: {}s", maxAge);

        return source;
    }

    // ❌ REMOVED: CorsWebFilter bean - CORS is now handled in SecurityConfig
    // Duplicate CORS configuration causes conflicts and 403 errors
}
