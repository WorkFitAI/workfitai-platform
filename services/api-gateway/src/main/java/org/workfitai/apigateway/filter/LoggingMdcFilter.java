package org.workfitai.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Global filter that sets up MDC (Mapped Diagnostic Context) for distributed
 * tracing
 * and structured logging. Adds request correlation headers for downstream
 * services.
 * 
 * Headers added to downstream requests:
 * - X-Request-Id: Unique identifier for request tracing
 * - X-Username: From JWT (if authenticated)
 * 
 * MDC context set:
 * - requestId: Unique request identifier
 * - username: User making the request
 * - path: Request path
 * - method: HTTP method
 */
@Component
@Slf4j
public class LoggingMdcFilter implements GlobalFilter, Ordered {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String USERNAME_HEADER = "X-Username";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Generate or extract request ID
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }

        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        final String finalRequestId = requestId;

        // Build mutated request with X-Request-Id header for downstream services
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(REQUEST_ID_HEADER, finalRequestId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

        // Extract username from security context (JWT) and set MDC
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth != null && auth.isAuthenticated())
                .filter(auth -> auth.getPrincipal() instanceof Jwt)
                .map(auth -> (Jwt) auth.getPrincipal())
                .map(jwt -> jwt.getSubject())
                .defaultIfEmpty("anonymous")
                .flatMap(username -> {
                    // Auto-detect log type
                    String logType = detectLogType(path, username);

                    // Set MDC context for this request (reactive context)
                    return chain.filter(mutatedExchange)
                            .contextWrite(ctx -> ctx
                                    .put("requestId", finalRequestId)
                                    .put("username", username)
                                    .put("path", path)
                                    .put("method", method)
                                    .put("log_type", logType))
                            .doOnSubscribe(sub -> {
                                MDC.put("requestId", finalRequestId);
                                MDC.put("username", username);
                                MDC.put("path", path);
                                MDC.put("method", method);
                                MDC.put("log_type", logType);
                                log.info("🚀 {} {} started [requestId={}, type={}]", method, path, finalRequestId,
                                        logType);
                            })
                            .doFinally(signalType -> {
                                log.info("✅ {} {} completed [requestId={}, signal={}]",
                                        method, path, finalRequestId, signalType);
                                MDC.clear();
                            });
                });
    }

    @Override
    public int getOrder() {
        // Run AFTER JwtClaimsExtractionFilter (-50) to have username available
        return -40;
    }

    /**
     * Auto-detect log type based on request characteristics.
     */
    private String detectLogType(String path, String username) {
        // Health checks and actuator endpoints
        if (path.contains("/actuator/") || path.endsWith("/health") || path.endsWith("/metrics")) {
            return "HEALTH_CHECK";
        }

        // Authentication endpoints (Gateway paths before stripping prefix)
        if (path.startsWith("/auth/")) {
            return "AUTH";
        }

        // User-initiated actions (authenticated users on business endpoints)
        if (username != null && !username.equals("anonymous") && !username.equals("system")) {
            return "USER_ACTION";
        }

        // Default to system logs
        return "SYSTEM";
    }
}
