package org.workfitai.apigateway.logging;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class RequestIdFilter implements WebFilter {
    @Value("${spring.application.name:api-gateway}") private String app;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String rid = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        if (rid == null || rid.isBlank()) rid = UUID.randomUUID().toString();

        exchange.getResponse().getHeaders().set("X-Request-Id", rid);
        MDC.put("requestId", rid);
        MDC.put("application", app);

        return chain.filter(exchange).doFinally(sig -> MDC.clear());
    }
}
