package org.workfitai.userservice.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.workfitai.userservice.service.UserService;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter to extract userId from JWT and set it as request attribute.
 * This allows controllers to access the current user's ID
 * via @RequestAttribute("userId").
 */
@Component
@Slf4j
public class UserIdExtractionFilter extends OncePerRequestFilter {

    private final UserService userService;

    /**
     * Constructor with @Lazy annotation to break circular dependency.
     * UserService is injected lazily to avoid:
     * SecurityConfig → UserIdExtractionFilter → UserService → SecurityConfig cycle
     */
    public UserIdExtractionFilter(@Lazy UserService userService) {
        this.userService = userService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.isAuthenticated()
                    && authentication.getPrincipal() instanceof Jwt) {
                Jwt jwt = (Jwt) authentication.getPrincipal();

                // Extract username from "sub" claim
                String username = jwt.getSubject();
                log.debug("JWT subject (username): {}", username);

                if (username != null && !username.isEmpty()) {
                    try {
                        // Look up userId by username
                        UUID userId = userService.findUserIdByUsername(username);

                        if (userId != null) {
                            // Set userId as request attribute for controllers
                            request.setAttribute("userId", userId.toString());
                            log.debug("✅ Set userId attribute: {} for username: {}", userId, username);
                        } else {
                            log.warn("❌ Could not find userId for username: {}", username);
                        }
                    } catch (Exception e) {
                        // Log error but don't block the request
                        // The controller will handle missing userId if needed
                        log.error("❌ Exception while extracting userId for username: {}", username, e);
                    }
                } else {
                    log.warn("JWT subject (username) is null or empty");
                }
            } else {
                log.debug("No JWT authentication found or not authenticated");
            }
        } catch (Exception e) {
            log.error("Unexpected error in UserIdExtractionFilter", e);
        }

        filterChain.doFilter(request, response);
    }
}
