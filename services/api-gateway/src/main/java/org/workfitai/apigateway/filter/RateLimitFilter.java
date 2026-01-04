package org.workfitai.apigateway.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.workfitai.apigateway.config.RateLimitConfig;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

/**
 * Rate Limiting Filter using Bucket4j with Redis backend
 * 
 * Features:
 * - Token bucket algorithm for smooth rate limiting
 * - Separate limits for authenticated vs anonymous users
 * - Endpoint-specific rate limits (e.g., /auth/login)
 * - Fail-open strategy (allow on error to prevent service disruption)
 * - Rate limit headers for client feedback
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final RateLimitConfig rateLimitConfig;
    private final LettuceConnectionFactory redisConnectionFactory;
    private volatile ProxyManager<String> proxyManager;

    @Override
    public int getOrder() {
        return -100; // Run early, before authentication
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // Skip rate limiting for actuator and health checks
        if (path.startsWith("/actuator") || path.startsWith("/health")) {
            return chain.filter(exchange);
        }

        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(ctx -> ctx.getAuthentication())
                .filter(auth -> auth != null && auth.isAuthenticated())
                .map(auth -> auth.getName())
                .defaultIfEmpty(getClientIp(exchange))
                .flatMap(identifier -> {
                    String bucketKey = "rate-limit:" + identifier + ":" + normalizePath(path);

                    try {
                        // Get or create bucket configuration
                        BucketConfiguration config = buildBucketConfig(path);
                        Bucket bucket = getProxyManager().builder().build(bucketKey, config);

                        // Try to consume 1 token
                        if (bucket.tryConsume(1)) {
                            // Add rate limit headers
                            long remaining = bucket.getAvailableTokens();
                            exchange.getResponse().getHeaders()
                                    .add("X-RateLimit-Remaining", String.valueOf(remaining));
                            return chain.filter(exchange);
                        } else {
                            // Rate limit exceeded
                            log.warn("⚠️ Rate limit exceeded: {} - {}", identifier, path);
                            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                            exchange.getResponse().getHeaders()
                                    .add("X-RateLimit-Retry-After", "60");
                            exchange.getResponse().getHeaders()
                                    .add("Content-Type", "application/json");

                            String errorBody = String.format(
                                    "{\"status\":429,\"message\":\"Rate limit exceeded. Please try again later.\",\"error\":\"TOO_MANY_REQUESTS\"}");

                            return exchange.getResponse()
                                    .writeWith(Mono.just(exchange.getResponse()
                                            .bufferFactory()
                                            .wrap(errorBody.getBytes())));
                        }
                    } catch (Exception e) {
                        log.error("❌ Rate limit error for key {}: {}", bucketKey, e.getMessage());
                        // Fail open - allow request on error
                        return chain.filter(exchange);
                    }
                })
                .onErrorResume(e -> {
                    log.error("❌ Rate limit context error: {}", e.getMessage());
                    // Fail open - allow request on error
                    return chain.filter(exchange);
                });
    }

    private BucketConfiguration buildBucketConfig(String path) {
        // Check endpoint-specific limits first
        for (RateLimitConfig.EndpointConfig endpoint : rateLimitConfig.getEndpoints()) {
            if (path.startsWith(endpoint.getPath())) {
                int refillTokens = endpoint.getRequestsPerMinute();
                int capacity = endpoint.getBurstCapacity();
                Bandwidth limit = Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(refillTokens, Duration.ofMinutes(1))
                        .build();
                log.debug("📊 Rate limit for {}: {} req/min, burst {}", path, refillTokens, capacity);
                return BucketConfiguration.builder().addLimit(limit).build();
            }
        }

        // Use global limits as default
        if (rateLimitConfig.getGlobal().isEnabled()) {
            int refillTokens = rateLimitConfig.getGlobal().getRequestsPerSecond();
            int capacity = rateLimitConfig.getGlobal().getBurstCapacity();
            Bandwidth limit = Bandwidth.builder()
                    .capacity(capacity)
                    .refillGreedy(refillTokens, Duration.ofSeconds(1))
                    .build();
            return BucketConfiguration.builder().addLimit(limit).build();
        }

        // Default fallback (should not happen if config is correct)
        Bandwidth limit = Bandwidth.builder()
                .capacity(100)
                .refillGreedy(100, Duration.ofSeconds(1))
                .build();
        return BucketConfiguration.builder().addLimit(limit).build();
    }

    private synchronized ProxyManager<String> getProxyManager() {
        if (proxyManager == null) {
            String redisUri = String.format("redis://%s:%d",
                    Objects.requireNonNull(redisConnectionFactory.getHostName()),
                    redisConnectionFactory.getPort());

            log.info("🔧 Initializing Bucket4j ProxyManager with Redis: {}", redisUri);

            RedisClient redisClient = RedisClient.create(redisUri);
            StatefulRedisConnection<String, byte[]> connection = redisClient.connect(
                    io.lettuce.core.codec.RedisCodec.of(
                            io.lettuce.core.codec.StringCodec.UTF8,
                            io.lettuce.core.codec.ByteArrayCodec.INSTANCE));
            proxyManager = LettuceBasedProxyManager.builderFor(connection)
                    .build();
        }
        return proxyManager;
    }

    private String getClientIp(ServerWebExchange exchange) {
        String ip = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
        } else {
            // X-Forwarded-For may contain multiple IPs, take the first one
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String normalizePath(String path) {
        // Normalize path for rate limiting (e.g., /auth/login/123 -> /auth/login)
        // This prevents abuse by adding random path segments
        if (path.startsWith("/auth/")) {
            return "/auth" + path.substring(5).replaceAll("/\\d+", "");
        }
        return path;
    }
}
