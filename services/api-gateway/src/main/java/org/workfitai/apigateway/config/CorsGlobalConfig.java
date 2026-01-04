package org.workfitai.apigateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
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
 * - Profile-based origin whitelisting (local, docker, production)
 * - Separate WebSocket CORS configuration for SockJS compatibility
 * - Production validation (MUST set ALLOWED_ORIGINS env var)
 * - Prevents 403 Invalid CORS errors for WebSocket connections
 */
@Configuration
@Slf4j
public class CorsGlobalConfig {

    private final Environment environment;

    @Value("${app.cors.allowed-origins.local:http://localhost:3000}")
    private String localOrigins;

    @Value("${app.cors.allowed-origins.docker:http://localhost:3000}")
    private String dockerOrigins;

    @Value("${app.cors.allowed-origins.production:}")
    private String prodOrigins;

    @Value("${app.cors.websocket-origins.local:http://localhost:3000}")
    private String localWsOrigins;

    @Value("${app.cors.websocket-origins.docker:http://localhost:3000}")
    private String dockerWsOrigins;

    @Value("${app.cors.websocket-origins.production:}")
    private String prodWsOrigins;

    @Value("${app.cors.max-age:3600}")
    private long maxAge;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,PATCH,OPTIONS}")
    private String allowedMethods;

    @Value("${app.cors.exposed-headers:X-Total-Count,X-Page-Number,X-Page-Size}")
    private String exposedHeaders;

    public CorsGlobalConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        String activeProfile = Arrays.stream(environment.getActiveProfiles())
                .findFirst()
                .orElse("docker");

        log.info("🌐 Configuring CORS for profile: {}", activeProfile);

        // Select origins based on active profile
        String apiOrigins = switch (activeProfile) {
            case "local" -> localOrigins;
            case "production", "prod" -> prodOrigins;
            default -> dockerOrigins;
        };

        String wsOrigins = switch (activeProfile) {
            case "local" -> localWsOrigins;
            case "production", "prod" -> prodWsOrigins;
            default -> dockerWsOrigins;
        };

        // Validate production origins
        if (("production".equals(activeProfile) || "prod".equals(activeProfile))
                && (apiOrigins.isEmpty() || apiOrigins.contains("*"))) {
            throw new IllegalStateException(
                    "⛔ PRODUCTION PROFILE REQUIRES EXPLICIT ALLOWED_ORIGINS! " +
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
