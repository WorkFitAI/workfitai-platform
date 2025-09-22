package org.workfitai.apigateway.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {
    @Value("${auth.jwt.secret}")
    private String jwtSecret;
    @Value("${auth.jwt.issuer}")
    private String issuer;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .logout(logout -> logout.disable())
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/**",
                                "/auth/login",
                                "/auth/refresh",
                                "/auth/register",
                                "/cv/**").permitAll()
                        .anyExchange().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((swe, err) ->
                                Mono.fromRunnable(() -> {
                                    swe.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                                })
                        )
                        .accessDeniedHandler((swe, err) ->
                                Mono.fromRunnable(() -> {
                                    swe.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                                })
                        )
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .build();
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder() {
        var key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var decoder = NimbusReactiveJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        var validators = new ArrayList<OAuth2TokenValidator<Jwt>>();
        validators.add(JwtValidators.createDefault());
        validators.add(new JwtIssuerValidator(issuer));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return decoder;
    }

}