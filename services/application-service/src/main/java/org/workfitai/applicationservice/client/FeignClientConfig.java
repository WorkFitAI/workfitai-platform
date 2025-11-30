package org.workfitai.applicationservice.client;

import feign.Logger;
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Configuration for Feign clients.
 * 
 * Provides:
 * 1. Request interceptor for propagating JWT tokens
 * 2. Logging configuration for debugging
 * 
 * Token Propagation:
 * - Extracts JWT from current SecurityContext
 * - Adds Authorization header to outgoing Feign requests
 * - Required for authenticated cross-service calls
 * 
 * Usage:
 * When application-service calls job-service or cv-service,
 * the user's JWT is automatically forwarded, allowing:
 * - CV ownership verification in cv-service
 * - Authorization checks in downstream services
 */
@Configuration
public class FeignClientConfig {

    /**
     * Interceptor that propagates JWT token to downstream services.
     * 
     * Flow:
     * 1. User calls application-service with JWT
     * 2. application-service extracts JWT from SecurityContext
     * 3. JWT is added to Feign request headers
     * 4. Downstream service (job/cv) receives authenticated request
     * 
     * Note: Returns empty if no authentication context (e.g., system calls)
     * 
     * @return RequestInterceptor for JWT propagation
     */
    @Bean
    public RequestInterceptor authorizationRequestInterceptor() {
        return requestTemplate -> {
            // Get current authentication from SecurityContext
            var authentication = SecurityContextHolder.getContext().getAuthentication();

            // Only propagate if we have a valid JWT authentication
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                String tokenValue = jwtAuth.getToken().getTokenValue();
                requestTemplate.header("Authorization", "Bearer " + tokenValue);
            }
        };
    }

    /**
     * Feign logging level for debugging.
     * 
     * Levels:
     * - NONE: No logging (production)
     * - BASIC: Request method, URL, response status, execution time
     * - HEADERS: BASIC + request/response headers
     * - FULL: HEADERS + request/response bodies (development only)
     * 
     * Controlled by: feign.client.config.default.loggerLevel in application.yml
     * 
     * @return Logger.Level for Feign clients
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
