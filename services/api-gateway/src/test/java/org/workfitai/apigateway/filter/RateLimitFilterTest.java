package org.workfitai.apigateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.apigateway.config.RateLimitConfig;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class RateLimitFilterTest {

    @Mock GatewayFilterChain chain;
    @Mock LettuceConnectionFactory redisFactory;

    RateLimitFilter filter;
    RateLimitConfig rateLimitConfig;

    // Bucket4j mocks — raw types used to avoid generic-bridge compile issues
    ProxyManager<String> mockProxyManager;
    RemoteBucketBuilder mockBuilder;
    BucketProxy mockBucket;

    @BeforeEach
    void setUp() {
        rateLimitConfig = new RateLimitConfig(); // defaults: global enabled, 100 req/s, burst 200
        filter = new RateLimitFilter(rateLimitConfig, redisFactory);

        mockProxyManager = mock(ProxyManager.class);
        mockBuilder = mock(RemoteBucketBuilder.class);
        mockBucket = mock(BucketProxy.class);
    }

    // ─── helper: inject a proxyManager mock that consumes or rejects tokens ───

    private void injectProxyManager(boolean tokenConsumed, long remainingAfterConsume) {
        when(mockProxyManager.builder()).thenReturn(mockBuilder);
        when(mockBuilder.build(anyString(), any(BucketConfiguration.class))).thenReturn(mockBucket);
        when(mockBucket.tryConsume(1)).thenReturn(tokenConsumed);
        if (tokenConsumed) {
            when(mockBucket.getAvailableTokens()).thenReturn(remainingAfterConsume);
        }
        ReflectionTestUtils.setField(filter, "proxyManager", mockProxyManager);
    }

    // ─── 6-1: /actuator path skipped ─────────────────────────────────────────

    @Test
    void filter_actuatorPath_chainCalledImmediatelySkippingRateLimit() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/actuator/health").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        // proxyManager was never injected → null; getProxyManager() never called for skip path
    }

    // ─── 6-2: /health path skipped ───────────────────────────────────────────

    @Test
    void filter_healthLivePath_chainCalledImmediatelySkippingRateLimit() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/health/live").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    // ─── 6-3: Anonymous user, token consumed → chain called + X-RateLimit-Remaining header

    @Test
    void filter_anonymousUser_tokenConsumed_chainCalledWithRateLimitRemainingHeader() {
        injectProxyManager(true, 99L);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/jobs").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.clearContext()))
                .verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"))
                .isEqualTo("99");
    }

    // ─── 6-4: Authenticated user → username used as bucket key prefix ─────────

    @Test
    void filter_authenticatedUser_tokenConsumed_bucketKeyContainsUsername() {
        // Manual setup — avoids duplicate build() stub that injectProxyManager would create
        when(mockProxyManager.builder()).thenReturn(mockBuilder);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        when(mockBuilder.build(keyCaptor.capture(), any(BucketConfiguration.class))).thenReturn(mockBucket);
        when(mockBucket.tryConsume(1)).thenReturn(true);
        when(mockBucket.getAvailableTokens()).thenReturn(49L);
        ReflectionTestUtils.setField(filter, "proxyManager", mockProxyManager);

        SecurityContext ctx = new SecurityContextImpl(
                new UsernamePasswordAuthenticationToken("alice", null, List.of()));

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/jobs").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ctx))))
                .verifyComplete();

        verify(chain).filter(exchange);
        assertThat(keyCaptor.getValue()).contains("alice");
    }

    // ─── 6-5: Token exhausted → 429 response, X-RateLimit-Retry-After, chain NOT called

    @Test
    void filter_tokenExhausted_returns429WithRetryAfterHeaderAndDoesNotCallChain() {
        injectProxyManager(false, 0L);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/jobs").build());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.clearContext()))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Retry-After"))
                .isEqualTo("60");
        verify(chain, never()).filter(any());
    }

    // ─── 6-6: Endpoint-specific config for /auth/login ────────────────────────

    @Test
    void filter_loginPath_endpointSpecificConfigApplied_chainCalledOnTokenConsumed() {
        RateLimitConfig.EndpointConfig loginCfg = new RateLimitConfig.EndpointConfig();
        loginCfg.setPath("/auth/login");
        loginCfg.setRequestsPerMinute(5);
        loginCfg.setBurstCapacity(10);
        rateLimitConfig.getEndpoints().add(loginCfg);

        injectProxyManager(true, 4L);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("http://localhost/auth/login").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.clearContext()))
                .verifyComplete();

        verify(chain).filter(exchange);
        assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining"))
                .isEqualTo("4");
    }

    // ─── 6-7: proxyManager.builder() throws → fail-open, chain called ─────────

    @Test
    void filter_proxyManagerBuilderThrows_failOpenChainCalledWithOriginalExchange() {
        when(mockProxyManager.builder()).thenThrow(new RuntimeException("Redis unavailable"));
        ReflectionTestUtils.setField(filter, "proxyManager", mockProxyManager);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/jobs").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.clearContext()))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    // ─── 6-9: X-Forwarded-For single IP → bucket key contains that IP ─────────

    @Test
    void filter_xForwardedForSingleIp_bucketKeyContainsThatIp() {
        when(mockProxyManager.builder()).thenReturn(mockBuilder);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        when(mockBuilder.build(keyCaptor.capture(), any(BucketConfiguration.class))).thenReturn(mockBucket);
        when(mockBucket.tryConsume(1)).thenReturn(true);
        when(mockBucket.getAvailableTokens()).thenReturn(50L);
        ReflectionTestUtils.setField(filter, "proxyManager", mockProxyManager);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/jobs")
                        .header("X-Forwarded-For", "203.0.113.5")
                        .build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.clearContext()))
                .verifyComplete();

        assertThat(keyCaptor.getValue()).contains("203.0.113.5");
    }

    // ─── 6-10: X-Forwarded-For multiple IPs → key contains only first IP ──────

    @Test
    void filter_xForwardedForMultipleIps_bucketKeyContainsFirstIpOnly() {
        when(mockProxyManager.builder()).thenReturn(mockBuilder);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        when(mockBuilder.build(keyCaptor.capture(), any(BucketConfiguration.class))).thenReturn(mockBucket);
        when(mockBucket.tryConsume(1)).thenReturn(true);
        when(mockBucket.getAvailableTokens()).thenReturn(50L);
        ReflectionTestUtils.setField(filter, "proxyManager", mockProxyManager);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/jobs")
                        .header("X-Forwarded-For", "203.0.113.5, 10.0.0.1, 172.16.0.1")
                        .build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.clearContext()))
                .verifyComplete();

        assertThat(keyCaptor.getValue()).contains("203.0.113.5");
        assertThat(keyCaptor.getValue()).doesNotContain("10.0.0.1");
    }

    // ─── 6-11: X-Real-IP (no X-Forwarded-For) → key contains that IP ─────────

    @Test
    void filter_xRealIpNoXForwardedFor_bucketKeyContainsThatIp() {
        when(mockProxyManager.builder()).thenReturn(mockBuilder);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        when(mockBuilder.build(keyCaptor.capture(), any(BucketConfiguration.class))).thenReturn(mockBucket);
        when(mockBucket.tryConsume(1)).thenReturn(true);
        when(mockBucket.getAvailableTokens()).thenReturn(50L);
        ReflectionTestUtils.setField(filter, "proxyManager", mockProxyManager);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/jobs")
                        .header("X-Real-IP", "198.51.100.42")
                        .build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.clearContext()))
                .verifyComplete();

        assertThat(keyCaptor.getValue()).contains("198.51.100.42");
    }

    // ─── 6-12: No IP headers, remoteAddress → key contains address host ────────

    @Test
    void filter_noIpHeaders_remoteAddress_bucketKeyContainsRemoteHost() {
        when(mockProxyManager.builder()).thenReturn(mockBuilder);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        when(mockBuilder.build(keyCaptor.capture(), any(BucketConfiguration.class))).thenReturn(mockBucket);
        when(mockBucket.tryConsume(1)).thenReturn(true);
        when(mockBucket.getAvailableTokens()).thenReturn(50L);
        ReflectionTestUtils.setField(filter, "proxyManager", mockProxyManager);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/jobs")
                        .remoteAddress(new InetSocketAddress("10.20.30.40", 12345))
                        .build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.clearContext()))
                .verifyComplete();

        assertThat(keyCaptor.getValue()).contains("10.20.30.40");
    }

    // ─── 6-13/6-14/6-15: Path normalization via private method ───────────────

    @Test
    void normalizePath_authLoginPath_returnsPathUnchanged() {
        String result = ReflectionTestUtils.invokeMethod(filter, "normalizePath", "/auth/login");
        assertThat(result).isEqualTo("/auth/login");
    }

    @Test
    void normalizePath_authPathWithNumericSegment_stripsNumericSegment() {
        String result = ReflectionTestUtils.invokeMethod(filter, "normalizePath", "/auth/login/123");
        assertThat(result).isEqualTo("/auth/login");
    }

    @Test
    void normalizePath_nonAuthPath_returnsPathUnchanged() {
        String result = ReflectionTestUtils.invokeMethod(filter, "normalizePath", "/api/users");
        assertThat(result).isEqualTo("/api/users");
    }

    @Test
    void normalizePath_authPathWithMultipleNumericSegments_stripsAllNumericSegments() {
        String result = ReflectionTestUtils.invokeMethod(filter, "normalizePath", "/auth/user/456/profile");
        assertThat(result).isEqualTo("/auth/user/profile");
    }

    // ─── 6-16: Global rate limit disabled → fallback default config used ──────

    @Test
    void filter_globalRateLimitDisabled_fallbackConfigUsedChainCalled() {
        rateLimitConfig.getGlobal().setEnabled(false); // disable global → fallback (100 cap/100 req/s)
        injectProxyManager(true, 99L);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/jobs").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.clearContext()))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    // ─── getOrder ─────────────────────────────────────────────────────────────

    @Test
    void getOrder_returnsNegative100() {
        assertThat(filter.getOrder()).isEqualTo(-100);
    }
}
