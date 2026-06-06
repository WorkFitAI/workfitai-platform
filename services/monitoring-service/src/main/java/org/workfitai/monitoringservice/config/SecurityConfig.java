package org.workfitai.monitoringservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.workfitai.monitoringservice.security.PublicKeyProvider;

import java.security.interfaces.RSAPublicKey;
import java.util.HashSet;

/**
 * Security configuration for monitoring-service.
 *
 * Validates JWTs using RSA public key from auth-service.
 * Enables method-level @PreAuthorize so AdminController and HrmController
 * role guards are actually enforced (they were no-ops without @EnableMethodSecurity).
 *
 * JWT claim mapping:
 *   "roles" → ROLE_* authorities (for hasRole checks)
 *   "perms" → direct authorities  (for hasAuthority checks)
 *   "sub"   → principal name (username)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

    private final PublicKeyProvider publicKeyProvider;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        try {
            RSAPublicKey publicKey = publicKeyProvider.getPublicKey();
            log.info("✅ JWT Decoder configured for monitoring-service");
            return NimbusJwtDecoder.withPublicKey(publicKey).build();
        } catch (Exception e) {
            log.error("❌ Failed to configure JWT Decoder: {}", e.getMessage());
            throw new IllegalStateException("Failed to load public key from auth-service", e);
        }
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        // "roles" claim → ROLE_* authorities
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName("roles");
        rolesConverter.setAuthorityPrefix("ROLE_");

        // "perms" claim → direct authorities (no prefix)
        JwtGrantedAuthoritiesConverter permsConverter = new JwtGrantedAuthoritiesConverter();
        permsConverter.setAuthoritiesClaimName("perms");
        permsConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            var authorities = new HashSet<>(rolesConverter.convert(jwt));
            authorities.addAll(permsConverter.convert(jwt));
            return authorities;
        });
        converter.setPrincipalClaimName("sub");

        return converter;
    }
}
