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
 * Validation Rules:
 * - Content-Type must be application/json for POST/PUT with body
 * - Content-Length must not exceed configured limits
 * - Required headers must be present
 * - Path parameters format validation
 */
@Component
@Slf4j
public class RequestValidationFilter implements GlobalFilter, Ordered {

    private static final long MAX_REQUEST_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            MediaType.APPLICATION_JSON_VALUE,
            MediaType.MULTIPART_FORM_DATA_VALUE,
            MediaType.APPLICATION_FORM_URLENCODED_VALUE);

    // Endpoints that require JSON content type
    private static final List<String> JSON_REQUIRED_PATHS = List.of(
            "/auth/login",
            "/auth/register",
            "/user/profile",
            "/job/",
            "/application/");

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

        // Validate Content-Type for POST/PUT/PATCH
        if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
            MediaType contentType = request.getHeaders().getContentType();

            // Require Content-Type for requests with body
            if (contentLength != null && contentLength > 0) {
                if (contentType == null) {
                    log.warn("❌ Request rejected - Missing Content-Type header for {}", path);
                    return rejectRequest(exchange, "Content-Type header is required",
                            HttpStatus.BAD_REQUEST);
                }

                // Validate Content-Type for JSON endpoints
                if (requiresJson(path) && !isJsonContentType(contentType)) {
                    log.warn("❌ Request rejected - Invalid Content-Type {} for {}", contentType, path);
                    return rejectRequest(exchange,
                            "Content-Type must be application/json for this endpoint",
                            HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                }

                // Check if Content-Type is allowed
                if (!isAllowedContentType(contentType)) {
                    log.warn("❌ Request rejected - Unsupported Content-Type {} for {}", contentType, path);
                    return rejectRequest(exchange,
                            "Unsupported Content-Type. Allowed types: " + String.join(", ", ALLOWED_CONTENT_TYPES),
                            HttpStatus.UNSUPPORTED_MEDIA_TYPE);
                }
            }
        }

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

    private boolean requiresJson(String path) {
        return JSON_REQUIRED_PATHS.stream().anyMatch(path::startsWith);
    }

    private boolean isJsonContentType(MediaType contentType) {
        return contentType != null &&
                (MediaType.APPLICATION_JSON.isCompatibleWith(contentType) ||
                        contentType.toString().contains("json"));
    }

    private boolean isAllowedContentType(MediaType contentType) {
        if (contentType == null)
            return false;

        return ALLOWED_CONTENT_TYPES.stream()
                .anyMatch(allowed -> contentType.toString().startsWith(allowed));
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
