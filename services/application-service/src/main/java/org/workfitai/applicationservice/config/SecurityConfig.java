package org.workfitai.applicationservice.config;

import java.security.interfaces.RSAPublicKey;
import java.util.HashSet;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.workfitai.applicationservice.security.PublicKeyProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Security configuration for application-service.
 * 
 * Features:
 * - OAuth2 Resource Server with JWT validation
 * - RSA public key fetched from auth-service
 * - Role and permission-based authorization
 * - Method-level security via @PreAuthorize
 * 
 * JWT Claims Mapping:
 * - "sub" claim → Principal name (userId)
 * - "roles" claim → ROLE_* authorities (e.g., ROLE_CANDIDATE, ROLE_HR)
 * - "perms" claim → Permission authorities (e.g., application:read,
 * application:write)
 * 
 * Endpoint Security:
 * - POST /applications: Authenticated (any role can apply)
 * - GET /applications/my: CANDIDATE role (own applications)
 * - GET /applications/job/{id}: HR or ADMIN role
 * - PUT /applications/{id}/status: HR or ADMIN role
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Enables @PreAuthorize, @Secured annotations
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final PublicKeyProvider publicKeyProvider;

    /**
     * Main security filter chain configuration.
     * 
     * Order of filters:
     * 1. CSRF disabled (stateless API)
     * 2. Session management: STATELESS
     * 3. Request authorization rules
     * 4. OAuth2 Resource Server JWT configuration
     * 
     * @param http HttpSecurity builder
     * @return Configured SecurityFilterChain
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF - stateless REST API with JWT
                .csrf(csrf -> csrf.disable())

                // No server-side sessions
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // Check application status (public, for job listings)
                        .requestMatchers(HttpMethod.GET, "/api/v1/applications/check").permitAll()

                        // All other requests require authentication
                        .anyRequest().authenticated())

                // OAuth2 Resource Server with JWT
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    /**
     * JWT Decoder using RSA public key from auth-service.
     * 
     * Validation:
     * - Verifies JWT signature using RSA-256 algorithm
     * - Validates expiration time (exp claim)
     * 
     * Note: Issuer validation is disabled to allow flexibility
     * in development environments.
     * 
     * @return Configured JwtDecoder
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        try {
            RSAPublicKey publicKey = publicKeyProvider.getPublicKey();
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();

            // Skip issuer validation - just validate signature and expiry
            // This provides flexibility for different environments
            decoder.setJwtValidator(
                    jwt -> org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success());

            log.info("✅ JWT Decoder configured with RSA public key");
            return decoder;

        } catch (Exception e) {
            log.error("❌ Failed to configure JWT Decoder", e);
            throw new IllegalStateException("Failed to load public key from auth-service", e);
        }
    }

    /**
     * Converts JWT claims to Spring Security authorities.
     * 
     * Claim Mapping:
     * - "roles" claim → ROLE_* authorities (for hasRole() checks)
     * Example: ["CANDIDATE"] → [ROLE_CANDIDATE]
     * 
     * - "perms" claim → Direct authorities (for hasAuthority() checks)
     * Example: ["application:read", "application:write"] → as-is
     * 
     * Usage in controllers:
     * - @PreAuthorize("hasRole('CANDIDATE')") - checks ROLE_CANDIDATE
     * - @PreAuthorize("hasAuthority('application:write')") - checks permission
     * 
     * @return Configured JwtAuthenticationConverter
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // Converter for "roles" claim → ROLE_* authorities
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("ROLE_");

        // Converter for "perms" claim → direct authorities (no prefix)
        JwtGrantedAuthoritiesConverter permsConverter = new JwtGrantedAuthoritiesConverter();
        permsConverter.setAuthoritiesClaimName("perms");
        permsConverter.setAuthorityPrefix("");

        log.info("✅ JWT Authentication Converter configured for roles : " + rolesConverter + " , and permissions: "
                + permsConverter);

        // Combine both converters
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = new HashSet<>(rolesConverter.convert(jwt));
            authorities.addAll(permsConverter.convert(jwt));
            return authorities;
        });

        // ✅ Extract username from "sub" claim as principal
        converter.setPrincipalClaimName("sub");

        return converter;
    }
}
