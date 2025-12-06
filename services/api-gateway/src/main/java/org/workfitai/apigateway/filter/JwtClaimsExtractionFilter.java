package org.workfitai.apigateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global filter that extracts claims from authenticated JWT tokens
 * and adds them as headers for downstream microservices.
 * 
 * Headers added:
 * - X-Username: The user's username (sub claim from JWT)
 * - X-User-Roles: The user's roles (comma-separated)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtClaimsExtractionFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth != null && auth.isAuthenticated())
                .filter(auth -> auth.getPrincipal() instanceof Jwt)
                .map(auth -> (Jwt) auth.getPrincipal())
                .flatMap(jwt -> {
                    // Extract claims from JWT
                    // JWT structure: subject = username, roles = list of roles, perms = permissions
                    String username = jwt.getSubject();
                    var roles = jwt.getClaimAsStringList("roles");

                    log.debug("[JwtClaimsFilter] 🎫 Extracted claims - username: {}, roles: {}",
                            username, roles);

                    // Build mutated request with additional headers
                    ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();

                    if (username != null) {
                        requestBuilder.header("X-Username", username);
                    }
                    if (roles != null && !roles.isEmpty()) {
                        requestBuilder.header("X-User-Roles", String.join(",", roles));
                    }

                    ServerHttpRequest mutatedRequest = requestBuilder.build();
                    ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

                    log.info("[JwtClaimsFilter] ✅ Added user headers for downstream service - username: {}", username);
                    return chain.filter(mutatedExchange);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("[JwtClaimsFilter] ⏭️ No authenticated JWT found, proceeding without user headers");
                    return chain.filter(exchange);
                }));
    }

    @Override
    public int getOrder() {
        // Run after security filters but before routing
        // Spring Security filters typically run at order -100
        return -50;
    }
}
