package org.workfitai.apigateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.workfitai.apigateway.security.PublicKeyProvider;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.interfaces.RSAPublicKey;
import java.util.Collection;
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

        /*
         * =========================================================
         * 1️⃣ SECURITY CHAIN: WEBSOCKET (NO JWT – HIGHEST PRIORITY)
         * =========================================================
         */
        /**
         * WebSocket Security Chain - COMPLETELY BYPASS Spring Security
         * Uses negateServerWebExchangeMatcher to exclude WebSocket paths from ALL security processing
         */
        // NOTE: WebSocket paths are excluded from this chain, see apiSecurityChain below

        /*
         * =========================================================
         * 2️⃣ SECURITY CHAIN: REST API (JWT REQUIRED)
         * =========================================================
         */
        @Bean
        @Order(0)
        public SecurityWebFilterChain apiSecurityChain(ServerHttpSecurity http) {
                return http
                                .cors(ServerHttpSecurity.CorsSpec::disable) // ❌ Disable - let CorsWebFilter handle it
                                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                                .logout(ServerHttpSecurity.LogoutSpec::disable)
                                .authorizeExchange(exchanges -> exchanges
                                                .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                                                // 🔓 PUBLIC ENDPOINTS
                                                .pathMatchers(
                                                                "/actuator/**",
                                                                "/auth/login",
                                                                "/auth/register",
                                                                "/auth/refresh",
                                                                "/auth/logout",
                                                                "/auth/verify-otp",
                                                                "/auth/verify-2fa-login",
                                                                "/auth/forgot-password",
                                                                "/auth/reset-password",
                                                                "/cv/public/**",
                                                                "/job/public/**",
                                                                "/monitoring-service/**",
                                                                "/debug/**",
                                                                "/user/actuator/**",
                                                                "/notification/ws/**")  // ✅ WebSocket permitAll
                                                .permitAll()

                                                // 🔒 EVERYTHING ELSE REQUIRES JWT
                                                .anyExchange().authenticated())
                                .exceptionHandling(e -> e
                                                .authenticationEntryPoint((swe, err) -> Mono.fromRunnable(() -> swe
                                                                .getResponse().setStatusCode(HttpStatus.UNAUTHORIZED)))
                                                .accessDeniedHandler((swe,
                                                                err) -> Mono.fromRunnable(() -> swe.getResponse()
                                                                                .setStatusCode(HttpStatus.FORBIDDEN))))
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                .jwt(jwt -> jwt
                                                                .jwtDecoder(jwtDecoder())
                                                                .jwtAuthenticationConverter(
                                                                                jwtAuthenticationConverter())))
                                .build();
        }

        /*
         * =========================================================
         * 3️⃣ JWT DECODER
         * =========================================================
         */
        @Bean
        public ReactiveJwtDecoder jwtDecoder() {
                RSAPublicKey key = publicKeyProvider.getPublicKey();
                log.info("🔐 [Gateway] Building JWT decoder with RSA public key");
                return NimbusReactiveJwtDecoder.withPublicKey(key).build();
        }

        /*
         * =========================================================
         * 4️⃣ JWT → AUTHORITIES CONVERTER
         * =========================================================
         */
        @Bean
        public ReactiveJwtAuthenticationConverter jwtAuthenticationConverter() {

                // roles → ROLE_*
                JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
                rolesConverter.setAuthoritiesClaimName("roles");
                rolesConverter.setAuthorityPrefix("ROLE_");

                // perms → raw authorities
                JwtGrantedAuthoritiesConverter permsConverter = new JwtGrantedAuthoritiesConverter();
                permsConverter.setAuthoritiesClaimName("perms");
                permsConverter.setAuthorityPrefix("");

                ReactiveJwtAuthenticationConverter converter = new ReactiveJwtAuthenticationConverter();

                converter.setJwtGrantedAuthoritiesConverter(jwt -> {
                        Collection<GrantedAuthority> roles = rolesConverter.convert(jwt);
                        Collection<GrantedAuthority> perms = permsConverter.convert(jwt);

                        Set<GrantedAuthority> authorities = Stream.concat(
                                        roles != null ? roles.stream() : Stream.empty(),
                                        perms != null ? perms.stream() : Stream.empty()).collect(Collectors.toSet());

                        log.debug("🔐 [JWT] Authorities: {}",
                                        authorities.stream()
                                                        .map(GrantedAuthority::getAuthority)
                                                        .collect(Collectors.joining(", ")));

                        return Flux.fromIterable(authorities);
                });

                return converter;
        }
}
