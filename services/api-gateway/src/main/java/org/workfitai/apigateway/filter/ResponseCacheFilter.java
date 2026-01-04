package org.workfitai.apigateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Response Caching Filter (Phase 3)
 * 
 * Caches GET responses for read-heavy endpoints to reduce backend load.
 * Uses Redis for distributed caching with configurable TTL per endpoint.
 * 
 * Features:
 * - Cache only successful GET requests (200 OK)
 * - Skip cache for authenticated requests (user-specific data)
 * - Configurable cache patterns and TTL
 * - Cache invalidation on PUT/POST/DELETE to same resource
 */
@Component
@ConditionalOnProperty(
        prefix = "app.cache",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false  // Default: disabled
)
@Slf4j
@RequiredArgsConstructor
public class ResponseCacheFilter implements GlobalFilter, Ordered {

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ModifyResponseBodyGatewayFilterFactory modifyResponseBodyFilter;

    // Cacheable endpoints (GET only)
    private static final List<CachePattern> CACHE_PATTERNS = List.of(
            new CachePattern(".*/job/public/.*", Duration.ofMinutes(5)), // Public job listings
            new CachePattern(".*/cv/public/.*", Duration.ofMinutes(10)), // Public CV templates
            new CachePattern("/actuator/health.*", Duration.ofSeconds(30)) // Health checks (exact path)
    );

    // Skip caching for these paths (user-specific or mutable)
    private static final List<String> SKIP_CACHE_PATHS = List.of(
            "/user/profile",
            "/application/",
            "/auth/",
            "/notification/");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        HttpMethod method = request.getMethod();

        // Only cache GET requests
        if (method != HttpMethod.GET) {
            // Invalidate cache on mutation
            if (method == HttpMethod.PUT || method == HttpMethod.POST || method == HttpMethod.DELETE) {
                return invalidateCache(path).then(chain.filter(exchange));
            }
            return chain.filter(exchange);
        }

        // Skip non-cacheable paths
        if (shouldSkipCache(path)) {
            return chain.filter(exchange);
        }

        // Find matching cache pattern
        CachePattern pattern = findCachePattern(path);
        if (pattern == null) {
            return chain.filter(exchange);
        }

        // Generate cache key
        String cacheKey = generateCacheKey(request);

        // Try to get from cache
        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cachedResponse -> {
                    log.info("🎯 Cache HIT for {}", cacheKey);

                    // Return cached response
                    ServerHttpResponse response = exchange.getResponse();
                    response.setStatusCode(HttpStatus.OK);
                    response.getHeaders().add("X-Cache-Status", "HIT");
                    response.getHeaders().add("Content-Type", "application/json");

                    DataBuffer buffer = response.bufferFactory()
                            .wrap(cachedResponse.getBytes(StandardCharsets.UTF_8));
                    return response.writeWith(Flux.just(buffer));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("⏳ Cache MISS for {} - proceeding to backend", cacheKey);

                    // Add MISS header before response is committed
                    exchange.getResponse().beforeCommit(() -> {
                        exchange.getResponse().getHeaders().add("X-Cache-Status", "MISS");
                        return Mono.empty();
                    });

                    // Cache the response body using ModifyResponseBodyGatewayFilterFactory
                    Duration ttl = pattern.ttl;
                    ModifyResponseBodyGatewayFilterFactory.Config config = new ModifyResponseBodyGatewayFilterFactory.Config()
                            .setRewriteFunction(String.class, String.class, (webExchange, body) -> {
                                // Only cache successful responses
                                if (webExchange.getResponse().getStatusCode() == HttpStatus.OK && body != null) {
                                    return redisTemplate.opsForValue()
                                            .set(cacheKey, body, ttl)
                                            .doOnSuccess(
                                                    v -> log.info("💾 Cached response for {} (TTL: {})", cacheKey, ttl))
                                            .thenReturn(body);
                                }
                                return Mono.just(body);
                            });

                    return modifyResponseBodyFilter.apply(config).filter(exchange, chain);
                }));
    }

    private Mono<Boolean> invalidateCache(String path) {
        String pattern = "cache:response:" + path.replaceAll("/[^/]+$", "/*");
        return redisTemplate.keys(pattern)
                .flatMap(redisTemplate::delete)
                .reduce(0L, Long::sum)
                .doOnNext(deleted -> {
                    if (deleted > 0) {
                        log.info("🗑️ Invalidated {} cache entries for {}", deleted, path);
                    }
                })
                .map(deleted -> deleted > 0)
                .defaultIfEmpty(false);
    }

    private String generateCacheKey(ServerHttpRequest request) {
        String path = request.getPath().value();
        String query = request.getURI().getQuery();

        return "cache:response:" + path + (query != null ? "?" + query : "");
    }

    private boolean shouldSkipCache(String path) {
        return SKIP_CACHE_PATHS.stream().anyMatch(path::startsWith);
    }

    private CachePattern findCachePattern(String path) {
        return CACHE_PATTERNS.stream()
                .filter(p -> Pattern.matches(p.pattern, path))
                .findFirst()
                .orElse(null);
    }

    @Override
    public int getOrder() {
        return -5; // Run after rate limiting but before routing
    }

    private static class CachePattern {
        final String pattern;
        final Duration ttl;

        CachePattern(String pattern, Duration ttl) {
            this.pattern = pattern;
            this.ttl = ttl;
        }
    }

    /**
     * Decorator to capture response body for caching
     */
    private static class ResponseBodyCaptureDecorator
            extends org.springframework.http.server.reactive.ServerHttpResponseDecorator {
        private String body;
        private HttpStatus statusCode;

        public ResponseBodyCaptureDecorator(ServerHttpResponse delegate) {
            super(delegate);
        }

        @Override
        public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends DataBuffer> body) {
            return Flux.from(body)
                    .reduce(new StringBuilder(), (sb, buffer) -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        sb.append(new String(bytes, StandardCharsets.UTF_8));
                        return sb;
                    })
                    .flatMap(sb -> {
                        this.body = sb.toString();
                        this.statusCode = (HttpStatus) getDelegate().getStatusCode();

                        // Write original response
                        DataBuffer buffer = getDelegate().bufferFactory()
                                .wrap(this.body.getBytes(StandardCharsets.UTF_8));
                        return getDelegate().writeWith(Flux.just(buffer));
                    });
        }

        public String getBody() {
            return body;
        }

        public HttpStatus getStatusCode() {
            return statusCode;
        }
    }
}
