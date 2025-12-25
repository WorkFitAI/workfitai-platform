package org.workfitai.apigateway.filter;

import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Propagates B3 trace headers to downstream services
 * Enables distributed tracing across the microservices architecture
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TracePropagationFilter implements GlobalFilter, Ordered {

    private final Tracer tracer;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var currentSpan = tracer.currentSpan();

        if (currentSpan != null) {
            var traceContext = currentSpan.context();

            // Add B3 headers for downstream services
            exchange = exchange.mutate()
                    .request(r -> r.headers(headers -> {
                        headers.set("X-B3-TraceId", traceContext.traceId());
                        headers.set("X-B3-SpanId", traceContext.spanId());

                        if (traceContext.parentId() != null) {
                            headers.set("X-B3-ParentSpanId", traceContext.parentId());
                        }

                        headers.set("X-B3-Sampled", "1");

                        log.debug("Propagating trace: traceId={}, spanId={}",
                                traceContext.traceId(), traceContext.spanId());
                    }))
                    .build();
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1; // After security, before routing
    }
}
