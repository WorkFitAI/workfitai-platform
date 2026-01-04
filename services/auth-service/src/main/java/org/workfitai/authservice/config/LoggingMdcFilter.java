package org.workfitai.authservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.workfitai.authservice.constants.LogType;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that extracts logging context from Gateway headers and sets MDC
 * for structured logging correlation across services.
 * 
 * Expected headers from API Gateway:
 * - X-Request-Id: Unique request correlation ID
 * - X-Username: Authenticated user's username
 * - X-User-Roles: User's roles (comma-separated)
 * 
 * Auto-detects log_type based on request characteristics.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class LoggingMdcFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String USERNAME_HEADER = "X-Username";
    public static final String ROLES_HEADER = "X-User-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            // Extract or generate request ID
            String requestId = request.getHeader(REQUEST_ID_HEADER);
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString().substring(0, 8);
            }

            // Extract user info from headers (set by API Gateway)
            String username = request.getHeader(USERNAME_HEADER);
            if (username == null || username.isBlank()) {
                username = "anonymous";
            }

            String roles = request.getHeader(ROLES_HEADER);
            String path = request.getRequestURI();
            String method = request.getMethod();

            // Set MDC context for logging
            MDC.put("requestId", requestId);
            MDC.put("username", username);
            MDC.put("path", path);
            MDC.put("method", method);
            if (roles != null) {
                MDC.put("roles", roles);
            }

            // Auto-detect and set log type based on request characteristics
            LogType logType = detectLogType(path, username);
            MDC.put("log_type", logType.name());

            // Add request ID to response for client correlation
            response.addHeader(REQUEST_ID_HEADER, requestId);

            log.debug("📥 {} {} [requestId={}, user={}, type={}]", method, path, requestId, username, logType);

            filterChain.doFilter(request, response);

        } finally {
            // Always clear MDC to prevent memory leaks
            MDC.clear();
        }
    }

    /**
     * Auto-detect log type based on request characteristics.
     */
    private LogType detectLogType(String path, String username) {
        // Health checks and actuator endpoints
        if (path.startsWith("/actuator/") || path.equals("/health") || path.equals("/metrics")) {
            return LogType.HEALTH_CHECK;
        }

        // Authentication endpoints (auth-service paths after gateway strips /auth
        // prefix)
        if (path.startsWith("/login") || path.startsWith("/register") ||
                path.startsWith("/verify") || path.startsWith("/forgot-password") ||
                path.startsWith("/reset-password") || path.startsWith("/refresh")) {
            return LogType.AUTH;
        }

        // User-initiated actions (authenticated users on business endpoints)
        if (username != null && !username.equals("anonymous") && !username.equals("system")) {
            return LogType.USER_ACTION;
        }

        // Default to system logs
        return LogType.SYSTEM;
    }
}
