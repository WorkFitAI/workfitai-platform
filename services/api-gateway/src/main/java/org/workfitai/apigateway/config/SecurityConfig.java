package org.workfitai.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;
import java.util.ArrayList;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@EnableConfigurationProperties(RsaKeyProperties.class)
public class SecurityConfig {

        private final RsaKeyProperties rsaKeys;

        @Value("${auth.jwt.issuer}")
        private String issuer;

        public SecurityConfig(RsaKeyProperties rsaKeys) {
                this.rsaKeys = rsaKeys;
        }

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
                                                                "/auth/logout",
                                                                "/cv/**")
                                                .permitAll()
                                                .anyExchange().authenticated())
                                .exceptionHandling(e -> e
                                                .authenticationEntryPoint((swe, err) -> Mono.fromRunnable(() -> {
                                                        swe.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                                                }))
                                                .accessDeniedHandler((swe, err) -> Mono.fromRunnable(() -> {
                                                        swe.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                                                })))
                                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                                .build();
        }

        @Bean
        public ReactiveJwtDecoder jwtDecoder() {
                // Use RSA public key for JWT verification with RS256
                var decoder = NimbusReactiveJwtDecoder.withPublicKey(rsaKeys.publicKey())
                                .signatureAlgorithm(SignatureAlgorithm.RS256)
                                .build();

                var validators = new ArrayList<OAuth2TokenValidator<Jwt>>();
                validators.add(JwtValidators.createDefault());
                validators.add(new JwtIssuerValidator(issuer));

                decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
                return decoder;
        }

}