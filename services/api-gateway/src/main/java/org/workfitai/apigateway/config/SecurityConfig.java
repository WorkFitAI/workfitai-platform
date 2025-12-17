package org.workfitai.apigateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.workfitai.apigateway.security.PublicKeyProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.interfaces.RSAPublicKey;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
@Slf4j
public class SecurityConfig {

        private final PublicKeyProvider publicKeyProvider;

        private final CorsConfigurationSource corsConfigurationSource;

        @Bean
        public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
                return http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                                .logout(ServerHttpSecurity.LogoutSpec::disable)
                                .authorizeExchange(exchanges -> exchanges
                                                .pathMatchers(
                                                                "/actuator/**",
                                                                "/auth/login",
                                                                "/auth/register",
                                                                "/auth/refresh",
                                                                "/auth/logout",
                                                                "/auth/verify-otp",
                                                                "/auth/verify-2fa-login",
                                                                "/cv/public/**",
                                                                "/job/public/**",
                                                                "/monitoring-service/**",
                                                                "/debug/**", // Debug endpoints
                                                                "/user/actuator/**" // User service health checks
                                                ).permitAll()
                                                .anyExchange().authenticated())
                                .exceptionHandling(e -> e
                                                .authenticationEntryPoint((swe, err) -> Mono
                                                                .fromRunnable(() -> swe.getResponse().setStatusCode(
                                                                                HttpStatus.UNAUTHORIZED)))
                                                .accessDeniedHandler((swe, err) -> Mono
                                                                .fromRunnable(() -> swe.getResponse()
                                                                                .setStatusCode(HttpStatus.FORBIDDEN))))
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt
                                                                .jwtDecoder(jwtDecoder())
                                                                .jwtAuthenticationConverter(
                                                                                jwtAuthenticationConverter())))
                                .build();
        }

        @Bean
        public ReactiveJwtDecoder jwtDecoder() {
                RSAPublicKey key = publicKeyProvider.getPublicKey();
                log.info("🔐 Building JWT decoder using loaded public key...");
                return NimbusReactiveJwtDecoder.withPublicKey(key).build();
        }

        /**
         * Converts JWT claims to Spring Security authorities.
         *
         * Extracts authorities from both "roles" and "perms" claims:
         * - "roles" claim → ROLE_* authorities (for hasRole() checks)
         * Example: ["CANDIDATE"] → [ROLE_CANDIDATE]
         *
         * - "perms" claim → Direct authorities (for hasAuthority() checks)
         * Example: ["application:create", "application:read"] → as-is
         *
         * This ensures the API Gateway properly populates the Authentication object
         * with authorities, allowing downstream services to perform authorization
         * checks.
         *
         * @return Configured ReactiveJwtAuthenticationConverter
         */
        @Bean
        public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {
                // Converter for "roles" claim → ROLE_* authorities
                JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
                rolesConverter.setAuthoritiesClaimName("roles");
                rolesConverter.setAuthorityPrefix("ROLE_");

                // Converter for "perms" claim → direct authorities (no prefix)
                JwtGrantedAuthoritiesConverter permsConverter = new JwtGrantedAuthoritiesConverter();
                permsConverter.setAuthoritiesClaimName("perms");
                permsConverter.setAuthorityPrefix("");

                // Combine both converters
                ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();
                converter.setJwtGrantedAuthoritiesConverter(jwt -> {
                        // Extract authorities from both converters
                        Collection<GrantedAuthority> roles = rolesConverter.convert(jwt);
                        Collection<GrantedAuthority> perms = permsConverter.convert(jwt);

                        // Combine both collections
                        Set<GrantedAuthority> authorities = Stream.concat(
                                        roles != null ? roles.stream() : Stream.empty(),
                                        perms != null ? perms.stream() : Stream.empty()).collect(Collectors.toSet());

                        log.info("🔐 [JWT Auth] Extracted {} authorities from JWT: {}",
                                        authorities.size(),
                                        authorities.stream()
                                                        .map(GrantedAuthority::getAuthority)
                                                        .collect(Collectors.joining(", ")));

                        return Flux.fromIterable(authorities);
                });

                return converter;
        }
}