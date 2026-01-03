package org.workfitai.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Request Validation Filter (Phase 3)
 *
 * Validates incoming requests early to reject malformed/invalid requests
 * before they reach backend services.
 *
 * Validation Rules (Transport-level only):
 * - Content-Length must not exceed configured limits
 * - Required headers must be present for authenticated endpoints
 *
 * Note: Content-Type validation is delegated to backend services to support
 * diverse endpoint requirements (JSON, multipart, form-urlencoded, etc.)
 */
@Component
@Slf4j
public class RequestValidationFilter implements GlobalFilter, Ordered {

    private static final long MAX_REQUEST_SIZE = 10 * 1024 * 1024; // 10MB

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        HttpMethod method = request.getMethod();
        String path = request.getPath().value();

        // Skip validation for GET/OPTIONS
        if (method == HttpMethod.GET || method == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // Skip validation for actuator/public endpoints
        if (path.startsWith("/actuator") || path.startsWith("/fallback")) {
            return chain.filter(exchange);
        }

        // Validate Content-Length
        Long contentLength = request.getHeaders().getContentLength();
        if (contentLength != null && contentLength > MAX_REQUEST_SIZE) {
            log.warn("❌ Request rejected - Size {} exceeds limit {}", contentLength, MAX_REQUEST_SIZE);
            return rejectRequest(exchange, "Request size exceeds maximum limit of 10MB",
                    HttpStatus.PAYLOAD_TOO_LARGE);
        }

        // Content-Type validation is delegated to backend services
        // Each service validates its own accepted content types and returns appropriate errors

        // Validate required headers for authenticated endpoints
        if (isProtectedPath(path)) {
            String authorization = request.getHeaders().getFirst("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                log.warn("❌ Request rejected - Missing or invalid Authorization header for {}", path);
                return rejectRequest(exchange, "Authorization header is required",
                        HttpStatus.UNAUTHORIZED);
            }
        }

        // All validations passed
        log.debug("✅ Request validation passed for {} {}", method, path);
        return chain.filter(exchange);
    }

    private boolean isProtectedPath(String path) {
        return !path.startsWith("/auth/") &&
                !path.startsWith("/actuator") &&
                !path.contains("/public/");
    }

    private Mono<Void> rejectRequest(ServerWebExchange exchange, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> error = Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "path", exchange.getRequest().getPath().value(),
                "timestamp", System.currentTimeMillis());

        String json = convertToJson(error);
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    private String convertToJson(Map<String, Object> map) {
        StringBuilder json = new StringBuilder("{");
        map.forEach((key, value) -> {
            if (json.length() > 1)
                json.append(",");
            json.append("\"").append(key).append("\":");
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else {
                json.append(value);
            }
        });
        json.append("}");
        return json.toString();
    }

    @Override
    public int getOrder() {
        return -10; // Run before rate limiting and caching
    }
}
