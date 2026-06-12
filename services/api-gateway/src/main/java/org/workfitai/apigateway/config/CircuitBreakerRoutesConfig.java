package org.workfitai.apigateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import static org.springframework.cloud.gateway.support.RouteMetadataUtils.RESPONSE_TIMEOUT_ATTR;

/**
 * Circuit Breaker Routes Configuration
 * 
 * Applies circuit breakers to specific routes for fault tolerance.
 * Each route can have custom circuit breaker settings and fallback endpoints.
 * 
 * Pattern: Service discovery (lb://) → Circuit Breaker → Fallback
 */
@Configuration
@Slf4j
public class CircuitBreakerRoutesConfig {

    @Bean
    public RouteLocator circuitBreakerRoutes(RouteLocatorBuilder builder) {
        log.info("🔧 Configuring Circuit Breaker routes");

        return builder.routes()
                // ========== Auth Service Routes ==========
                .route("auth-login-cb", r -> r
                        .path("/auth/login")
                        .and().method(HttpMethod.POST)
                        .filters(f -> f
                                .stripPrefix(1)
                                .circuitBreaker(c -> c
                                        .setName("auth")
                                        .setFallbackUri("forward:/fallback/auth/login"))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3))) // No backoff (immediate retry)
                        .uri("lb://auth"))

                .route("auth-register-cb", r -> r
                        .path("/auth/register")
                        .and().method(HttpMethod.POST)
                        .filters(f -> f
                                .stripPrefix(1)
                                .circuitBreaker(c -> c
                                        .setName("auth")
                                        .setFallbackUri("forward:/fallback/auth/register"))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(2))) // No backoff
                        .uri("lb://auth"))

                // ========== User Service Routes ==========
                .route("user-profile-get-cb", r -> r
                        .path("/user/profile")
                        .and().method(HttpMethod.GET)
                        .filters(f -> f
                                .stripPrefix(1)
                                .circuitBreaker(c -> c
                                        .setName("user")
                                        .setFallbackUri("forward:/fallback/user/profile"))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setStatuses(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.BAD_GATEWAY,
                                                HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT)
                                        .setBackoff(Duration.ofMillis(100), Duration.ofMillis(500), 2, true)))
                        .uri("lb://user"))

                .route("user-profile-update-cb", r -> r
                        .path("/user/profile")
                        .and().method(HttpMethod.PUT)
                        .filters(f -> f
                                .stripPrefix(1)
                                .circuitBreaker(c -> c
                                        .setName("user")
                                        .setFallbackUri("forward:/fallback/user/profile"))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(1))) // Only 1 retry for updates
                        .uri("lb://user"))

                // ========== Job Service Routes ==========
                // Dedicated route for recommendation endpoints with its own circuit breaker.
                // Uses "job-recommendations" CB (not "job") so slow/failing AI calls do not
                // trip the circuit breaker for regular job requests.
                // Response timeout (120s) > CB timeLimiter (90s) > Feign readTimeout (60s).
                // No retries: the AI call is expensive; one attempt is enough.
                .route("job-recommendations-cb", r -> r
                        .path("/job/public/recommendations/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .circuitBreaker(c -> c
                                        .setName("job-recommendations")
                                        .setFallbackUri("forward:/fallback/job")))
                        .metadata(RESPONSE_TIMEOUT_ATTR, Duration.ofSeconds(120))
                        .uri("lb://job"))

                .route("job-service-cb", r -> r
                        .path("/job/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .circuitBreaker(c -> c
                                        .setName("job")
                                        .setFallbackUri("forward:/fallback/job"))
                                // SERVICE_UNAVAILABLE removed: CB-open fallback returns 503 and must
                                // never be retried — retrying amplifies failure counts and keeps CB open.
                                .retry(retryConfig -> retryConfig
                                        .setRetries(2)
                                        .setStatuses(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.BAD_GATEWAY,
                                                HttpStatus.GATEWAY_TIMEOUT)
                                        .setBackoff(Duration.ofMillis(100), Duration.ofMillis(500), 2, true)))
                        .uri("lb://job"))

                // ========== CV Service Routes ==========
                .route("cv-upload-cb", r -> r
                        .path("/cv/upload")
                        .and().method(HttpMethod.POST)
                        .filters(f -> f
                                .stripPrefix(1)
                                .circuitBreaker(c -> c
                                        .setName("cv")
                                        .setFallbackUri("forward:/fallback/cv/upload"))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(1))) // Don't retry file uploads
                        .uri("lb://cv"))

                // ========== Application Service Routes ==========
                .route("application-service-cb", r -> r
                        .path("/application/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .circuitBreaker(c -> c
                                        .setName("application")
                                        .setFallbackUri("forward:/fallback/application"))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(2)
                                        .setStatuses(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.BAD_GATEWAY,
                                                HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT)
                                        .setBackoff(Duration.ofMillis(100), Duration.ofMillis(500), 2, true)))
                        .uri("lb://application"))

                // ========== Notification Service Routes ==========
                .route("notification-service-cb", r -> r
                        .path("/notification/api/**") // Only REST API, not WebSocket
                        .filters(f -> f
                                .stripPrefix(2) // Strip /notification/api → service expects /health, /ws, etc.
                                .circuitBreaker(c -> c
                                        .setName("notification")
                                        .setFallbackUri("forward:/fallback/notification"))
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setStatuses(HttpStatus.INTERNAL_SERVER_ERROR, HttpStatus.BAD_GATEWAY,
                                                HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.GATEWAY_TIMEOUT)
                                        .setBackoff(Duration.ofMillis(100), Duration.ofMillis(500), 2, true)))
                        .uri("lb://notification"))

                .build();
    }
}
