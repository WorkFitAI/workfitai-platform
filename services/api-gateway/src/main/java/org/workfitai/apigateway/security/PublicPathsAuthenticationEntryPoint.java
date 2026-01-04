package org.workfitai.apigateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

/**
 * Custom authentication entry point that allows public paths to proceed
 * without JWT authentication, even if oauth2ResourceServer is configured.
 */
@Component
@Slf4j
public class PublicPathsAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private static final List<String> PUBLIC_PATHS = Arrays.asList(
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/auth/logout",
            "/auth/verify-otp",
            "/auth/verify-2fa-login",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/verify-reset-otp",
            "/auth/oauth" // All OAuth paths
    );

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        String path = exchange.getRequest().getURI().getPath();

        // Check if path is public
        boolean isPublic = PUBLIC_PATHS.stream().anyMatch(path::startsWith);

        if (isPublic) {
            log.debug("Public path accessed without JWT: {}", path);
            // Don't return 401 for public paths, let them proceed
            return Mono.empty();
        }

        // For protected paths, return 401
        log.warn("Unauthorized access attempt to protected path: {}", path);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();
    }
}
