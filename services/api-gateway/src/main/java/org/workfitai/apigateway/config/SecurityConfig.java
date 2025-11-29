package org.workfitai.apigateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.workfitai.apigateway.security.PublicKeyProvider;
import reactor.core.publisher.Mono;

import java.security.interfaces.RSAPublicKey;

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
                                                                "/cv/**",
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
                                                .jwt(jwt -> jwt.jwtDecoder(jwtDecoder())))
                                .build();
        }

        @Bean
        public ReactiveJwtDecoder jwtDecoder() {
                RSAPublicKey key = publicKeyProvider.getPublicKey();
                log.info("🔐 Building JWT decoder using loaded public key...");
                return NimbusReactiveJwtDecoder.withPublicKey(key).build();
        }
}
