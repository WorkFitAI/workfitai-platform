package org.workfitai.apigateway.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.workfitai.apigateway.service.IOpaqueTokenService;
import reactor.core.publisher.Mono;

/**
 * High priority WebFilter to convert opaque tokens to JWT before security
 * processing
 * This filter runs BEFORE Spring Security filters to ensure opaque tokens are
 * converted
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class OpaqueToJwtWebFilter implements WebFilter {

    private final IOpaqueTokenService opaqueTokenService;

    @Override
    @SuppressWarnings("null")
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String auth = exchange.getRequest().getHeaders().getFirst("Authorization");

        if (!StringUtils.hasText(auth)) {
            log.debug("[OpaqueWebFilter] ❌ No Authorization header");
            return chain.filter(exchange);
        }

        // Extract token from "Bearer token" format - auth is guaranteed non-null here
        final String token;
        if (auth.toLowerCase().startsWith("bearer ")) {
            token = auth.substring(7);
        } else {
            token = auth;
        }

        if (!StringUtils.hasText(token)) {
            log.debug("[OpaqueWebFilter] ❌ Empty token after processing");
            return chain.filter(exchange);
        }

        // Check if it's already a JWT (has 2 dots and reasonable length)
        if (token.chars().filter(ch -> ch == '.').count() == 2 && token.length() > 50) {
            log.debug("[OpaqueWebFilter] 🟢 Already JWT token, skip convert");
            return chain.filter(exchange);
        }

        // Convert opaque to JWT
        log.info("[OpaqueWebFilter] 🔄 Converting opaque -> jwt for token={}",
                token.substring(0, Math.min(8, token.length())));

        return opaqueTokenService.toJwt(token)
                .doOnNext(jwt -> log.info("[OpaqueWebFilter] ✅ Found JWT for opaque={}, jwt_prefix={}",
                        token.substring(0, Math.min(8, token.length())), jwt.substring(0, Math.min(20, jwt.length()))))
                .defaultIfEmpty("")
                .flatMap(jwt -> {
                    if (!StringUtils.hasText(jwt)) {
                        log.warn("[OpaqueWebFilter] ⚠️ No JWT found in Redis for opaque={}",
                                token.substring(0, Math.min(8, token.length())));
                        return chain.filter(exchange);
                    }

                    // Replace Authorization header with JWT
                    ServerHttpRequest mutated = exchange.getRequest().mutate()
                            .headers(h -> {
                                h.set("Authorization", "Bearer " + jwt);
                                h.set("X-Token-Source", "opaque");
                                h.set("X-Original-Opaque", token);
                            })
                            .build();

                    log.info("[OpaqueWebFilter] 🧩 Replaced Authorization header with JWT");
                    return chain.filter(exchange.mutate().request(mutated).build());
                })
                .doOnError(err -> log.error("[OpaqueWebFilter] 💥 Error converting opaque={}: {}",
                        token.substring(0, Math.min(8, token.length())), err.getMessage()))
                .onErrorResume(err -> {
                    log.error("[OpaqueWebFilter] 🚨 Fallback: proceeding without conversion due to error");
                    return chain.filter(exchange);
                });
    }
}