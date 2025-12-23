package org.workfitai.authservice.security;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATHS = new AntPathMatcher();

    // Endpoints that never require a token
    private static final List<String> PUBLIC_PATHS = List.of(
            "/", // health on root (your controller)
            "/register",
            "/login",
            "/refresh",
            "/verify-otp",
            "/verify-2fa-login",
            "/forgot-password",
            "/reset-password",
            "/verify-reset-otp",
            "/oauth/authorize/**", // OAuth authorize (public - creates URL)
            "/oauth/callback/**", // OAuth callback (public - browser redirect)
            "/actuator/**",
            "/error",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html");

    private final JwtService jwtService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip all preflight requests
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()))
            return true;

        String path = request.getRequestURI();
        for (String p : PUBLIC_PATHS) {
            if (PATHS.match(p, path))
                return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req,
            HttpServletResponse res,
            FilterChain chain)
            throws ServletException, IOException {

        System.out.println("Filtering request: " + req.getRequestURI());

        String header = req.getHeader("Authorization");
        if (StringUtils.hasText(header)) {
            String token = header;

            // Extract JWT from "Bearer <jwt>" format
            if (header.toLowerCase().startsWith("bearer ")) {
                token = header.substring(7); // Remove "Bearer " prefix
            }

            System.out.println("Extracted token: " + token.substring(0, Math.min(20, token.length())) + "...");

            try {
                if (jwtService.validateToken(token)) {
                    Claims claims = jwtService.getClaims(token);
                    String username = claims.getSubject();

                    @SuppressWarnings("unchecked")
                    var roles = (List<String>) claims.getOrDefault("roles", List.of());
                    @SuppressWarnings("unchecked")
                    var perms = (List<String>) claims.getOrDefault("perms", List.of());

                    var authorities = Stream.concat(roles.stream(), perms.stream())
                            .map(SimpleGrantedAuthority::new)
                            .toList();

                    System.out.println("Authenticated user: " + username + " with authorities: " + authorities);

                    var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception e) {
                // JWT invalid/expired/malformed → Don't set authentication
                // Spring Security will handle this as 401 Unauthorized
                System.out.println("JWT validation failed: " + e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(req, res);
    }
}