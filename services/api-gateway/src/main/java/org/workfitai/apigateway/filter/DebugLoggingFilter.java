package org.workfitai.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Debug filter to log all requests and headers for troubleshooting
 */
@Configuration
@Slf4j
public class DebugLoggingFilter {

    @Bean
    public GlobalFilter debugFilter() {
        return new DebugFilterImpl();
    }

    static final class DebugFilterImpl implements GlobalFilter, Ordered {

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE + 1; // Run after OpaqueToJwtPreFilter
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            String path = exchange.getRequest().getPath().value();
            String method = exchange.getRequest().getMethod().name();

            // Log request details
            log.info("🔍 [DEBUG] {} {} - Headers:", method, path);
            exchange.getRequest().getHeaders().forEach((key, values) -> {
                if (key.equalsIgnoreCase("Authorization")) {
                    String auth = values.get(0);
                    if (auth != null) {
                        // Show only first 20 chars for security
                        log.info("🔍 [DEBUG]   {}: {}", key, auth.substring(0, Math.min(20, auth.length())) + "...");
                    }
                } else if (key.startsWith("X-")) {
                    log.info("🔍 [DEBUG]   {}: {}", key, values);
                }
            });

            // Log exchange attributes
            if (exchange.getAttributes().containsKey(OpaqueToJwtPreFilter.ATTR_OPAQUE_USED)) {
                String opaqueUsed = (String) exchange.getAttributes().get(OpaqueToJwtPreFilter.ATTR_OPAQUE_USED);
                log.info("🔍 [DEBUG] Opaque token in use: {}",
                        opaqueUsed.substring(0, Math.min(8, opaqueUsed.length())));
            }

            return chain.filter(exchange);
        }
    }
}