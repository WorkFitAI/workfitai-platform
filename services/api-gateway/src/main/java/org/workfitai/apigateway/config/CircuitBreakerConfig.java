package org.workfitai.apigateway.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/**
 * Circuit Breaker Configuration using Resilience4j
 * 
 * Provides fault tolerance for downstream services:
 * - Prevents cascading failures
 * - Automatic recovery attempts
 * - Fallback responses
 * - Metrics collection
 * 
 * Circuit States:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Service is failing, requests immediately fail (return fallback)
 * - HALF_OPEN: Testing if service recovered (limited test requests)
 */
@Configuration
@Slf4j
public class CircuitBreakerConfig {

    /**
     * Default Circuit Breaker Configuration
     *
     * Settings:
     * - Sliding window: 10 calls (count-based)
     * - Failure threshold: 50% (5 out of 10 calls fail → OPEN)
     * - Slow call threshold: 60% calls slower than 3s → OPEN
     * - Wait in OPEN state: 30 seconds before trying HALF_OPEN
     * - Test calls in HALF_OPEN: 5 calls
     * - Timeout: 10 seconds per call
     * - Ignores client errors (4xx) - only trips on server errors (5xx) and timeouts
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> defaultCustomizer() {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        // Sliding window configuration
                        .slidingWindowType(SlidingWindowType.COUNT_BASED)
                        .slidingWindowSize(10) // Monitor last 10 calls

                        // Failure rate threshold
                        .failureRateThreshold(50.0f) // Open circuit if 50% fail
                        .slowCallRateThreshold(60.0f) // Consider slow calls as failures
                        .slowCallDurationThreshold(Duration.ofSeconds(3)) // Calls > 3s are slow

                        // Circuit state durations
                        .waitDurationInOpenState(Duration.ofSeconds(30)) // Wait 30s before retry
                        .permittedNumberOfCallsInHalfOpenState(5) // Test with 5 calls

                        // What counts as failure - ignore client errors (4xx), only record server errors (5xx)
                        .recordException(throwable -> {
                            if (throwable instanceof WebClientResponseException webClientException) {
                                int statusCode = webClientException.getStatusCode().value();
                                // Only record server errors (5xx) as failures
                                // Ignore client errors (4xx) - they're not service failures
                                return statusCode >= 500;
                            }
                            // Record all other exceptions (timeouts, connection errors, etc.)
                            return true;
                        })

                        // Minimum calls before calculating failure rate
                        .minimumNumberOfCalls(5) // Need at least 5 calls to open circuit

                        // Automatic transition from OPEN to HALF_OPEN
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)

                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(10)) // 10s timeout per request
                        .cancelRunningFuture(true) // Cancel if timeout
                        .build())
                .build());
    }

    /**
     * Auth Service Circuit Breaker
     * More strict - auth is critical
     * Ignores client errors (4xx) - only trips on server errors (5xx) and timeouts
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> authServiceCustomizer() {
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .slidingWindowSize(20) // Larger window for critical service
                        .failureRateThreshold(30.0f) // More sensitive - open at 30%
                        .slowCallRateThreshold(50.0f)
                        .slowCallDurationThreshold(Duration.ofSeconds(2)) // Faster timeout
                        .waitDurationInOpenState(Duration.ofSeconds(60)) // Longer recovery time
                        .permittedNumberOfCallsInHalfOpenState(3)
                        .minimumNumberOfCalls(10)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        // Ignore client errors (4xx), only record server errors (5xx)
                        .recordException(throwable -> {
                            if (throwable instanceof WebClientResponseException webClientException) {
                                return webClientException.getStatusCode().value() >= 500;
                            }
                            return true; // Record timeouts, connection errors, etc.
                        })
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(5)) // Shorter timeout for auth
                        .cancelRunningFuture(true)
                        .build())
                .build(), "auth");
    }

    /**
     * User Service Circuit Breaker
     * Balanced configuration
     * Ignores client errors (4xx) - only trips on server errors (5xx) and timeouts
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> userServiceCustomizer() {
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .slidingWindowSize(15)
                        .failureRateThreshold(40.0f)
                        .slowCallRateThreshold(60.0f)
                        .slowCallDurationThreshold(Duration.ofSeconds(3))
                        .waitDurationInOpenState(Duration.ofSeconds(45))
                        .permittedNumberOfCallsInHalfOpenState(5)
                        .minimumNumberOfCalls(8)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        // Ignore client errors (4xx), only record server errors (5xx)
                        .recordException(throwable -> {
                            if (throwable instanceof WebClientResponseException webClientException) {
                                return webClientException.getStatusCode().value() >= 500;
                            }
                            return true; // Record timeouts, connection errors, etc.
                        })
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(8))
                        .cancelRunningFuture(true)
                        .build())
                .build(), "user");
    }

    /**
     * Job Service Circuit Breaker
     * More lenient - can handle slower responses
     * Ignores client errors (4xx) - only trips on server errors (5xx) and timeouts
     */
    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> jobServiceCustomizer() {
        return factory -> factory.configure(builder -> builder
                .circuitBreakerConfig(io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .failureRateThreshold(60.0f) // More tolerant
                        .slowCallRateThreshold(70.0f)
                        .slowCallDurationThreshold(Duration.ofSeconds(5)) // Allow slower calls
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .permittedNumberOfCallsInHalfOpenState(5)
                        .minimumNumberOfCalls(5)
                        .automaticTransitionFromOpenToHalfOpenEnabled(true)
                        // Ignore client errors (4xx), only record server errors (5xx)
                        .recordException(throwable -> {
                            if (throwable instanceof WebClientResponseException webClientException) {
                                int statusCode = webClientException.getStatusCode().value();
                                return statusCode >= 500; // Only record 5xx as failures
                            }
                            return true; // Record timeouts, connection errors, etc.
                        })
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom()
                        .timeoutDuration(Duration.ofSeconds(15)) // Longer timeout
                        .cancelRunningFuture(true)
                        .build())
                .build(), "job");
    }
}
