package org.workfitai.userservice.config;

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

            // Add request ID to response for client correlation
            response.addHeader(REQUEST_ID_HEADER, requestId);

            log.debug("📥 {} {} [requestId={}, user={}]", method, path, requestId, username);

            filterChain.doFilter(request, response);

        } finally {
            // Always clear MDC to prevent memory leaks
            MDC.clear();
        }
    }
}
