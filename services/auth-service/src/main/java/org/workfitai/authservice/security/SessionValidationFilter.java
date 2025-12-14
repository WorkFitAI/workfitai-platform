package org.workfitai.authservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.workfitai.authservice.repository.UserSessionRepository;

import java.io.IOException;

/**
 * Filter to validate that authenticated users have at least one active session.
 * 
 * This prevents the scenario where:
 * 1. User changes password → all sessions deleted
 * 2. JWT still valid (not expired yet)
 * 3. User can still access endpoints → BAD
 * 
 * This filter checks if the user has ANY active session.
 * If not → 401 Unauthorized (JWT is technically valid but user logged out)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionValidationFilter extends OncePerRequestFilter {

    private final UserSessionRepository sessionRepository;

    private static final String[] SKIP_PATHS = {
            "/login",
            "/register",
            "/refresh",
            "/verify-otp",
            "/actuator",
            "/error"
    };

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String skip : SKIP_PATHS) {
            if (path.contains(skip)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        // Only validate if user is authenticated (JWT passed Spring Security)
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName();

            // Check if user has any active sessions
            long sessionCount = sessionRepository.countByUserId(username);

            if (sessionCount == 0) {
                log.warn("🚫 User {} has valid JWT but NO active sessions (logged out or password changed)", username);

                // Clear security context
                SecurityContextHolder.clearContext();

                // Return 401 Unauthorized
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("""
                        {
                            "status": 401,
                            "message": "Session expired. Please login again.",
                            "timestamp": "%s"
                        }
                        """.formatted(java.time.LocalDateTime.now()));
                return;
            }

            log.debug("✅ User {} has {} active session(s)", username, sessionCount);
        }

        chain.doFilter(request, response);
    }
}
