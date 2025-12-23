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
import org.workfitai.authservice.repository.UserRepository;
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
    private final UserRepository userRepository;

    private static final String[] SKIP_PATHS = {
            "/login",
            "/register",
            "/refresh",
            "/verify-otp",
            "/verify-2fa-login", // Allow 2FA login verification
            "/verify-reset-otp",
            "/change-password", // Allow password change even if sessions will be deleted
            "/reset-password", // Allow password reset (uses token, not session)
            "/forgot-password", // Public endpoint
            "/set-password", // Allow OAuth users to set password (may not have sessions yet)
            "/oauth", // OAuth endpoints
            "/authorize", // OAuth authorize
            "/callback", // OAuth callback
            "/sessions", // Allow checking sessions (returns empty list if no sessions)
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

            // Get userId from username (sessions are stored with userId, not username)
            String userId = userRepository.findByUsername(username)
                    .map(user -> user.getId())
                    .orElse(null);

            if (userId == null) {
                log.warn("🚫 User {} not found in database", username);
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("""
                        {
                            "status": 401,
                            "message": "User not found. Please login again.",
                            "timestamp": "%s"
                        }
                        """.formatted(java.time.LocalDateTime.now()));
                return;
            }

            // Check if user has any active sessions
            long sessionCount = sessionRepository.countByUserId(userId);

            if (sessionCount == 0) {
                log.warn(
                        "🚫 User {} (userId: {}) has valid JWT but NO active sessions (logged out or password changed)",
                        username, userId);

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
