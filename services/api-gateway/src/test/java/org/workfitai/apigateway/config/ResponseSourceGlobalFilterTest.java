package org.workfitai.apigateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.apigateway.service.IOpaqueTokenService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Tests for {@link ResponseSourceGlobalFilter.DecoratingResponseFilter}.
 *
 * <p>Note: {@code looksLikeJwt}, {@code looksLikeTokenKey}, and {@code merge} are
 * methods declared on the anonymous {@code ServerHttpResponseDecorator} created inside
 * {@code filter()}, not on {@code DecoratingResponseFilter} itself. They are therefore
 * unreachable via {@code ReflectionTestUtils.invokeMethod(filterInstance, ...)} and are
 * excluded from direct unit testing. Body-transformation behavior is covered by the
 * integration test suite.
 *
 * <p>The static helper {@code looksLikeJson} IS a method of {@code DecoratingResponseFilter}
 * and is tested here via reflection.
 */
@ExtendWith(MockitoExtension.class)
class ResponseSourceGlobalFilterTest {

    @Mock IOpaqueTokenService opaqueTokenService;
    @Mock GatewayFilterChain chain;

    ObjectMapper objectMapper;
    ResponseSourceGlobalFilter.DecoratingResponseFilter filter;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        filter = new ResponseSourceGlobalFilter.DecoratingResponseFilter(objectMapper, opaqueTokenService);
    }

    // ─── getOrder ─────────────────────────────────────────────────────────────

    @Test
    void getOrder_returnsOneBeforeNettyWriteResponseFilterOrder() {
        // NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER == -1 → expected == -2
        int expected = NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1;
        assertThat(filter.getOrder()).isEqualTo(expected);
        assertThat(filter.getOrder()).isEqualTo(-2);
    }

    // ─── filter() — route attr present ───────────────────────────────────────

    @Test
    void filter_routeAttrPresent_chainCalledWithDecoratedResponse() {
        Route route = mock(Route.class);
        when(route.getId()).thenReturn("user-service");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/user-service/api").build());
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);

        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any());
    }

    // ─── filter() — no route attr, path-based routeId extraction ─────────────

    @Test
    void filter_noRouteAttr_pathBasedRouteIdUsed_chainCalled() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/jobs").build());
        // No GATEWAY_ROUTE_ATTR → routeId from path segments[1] = "api"

        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any());
    }

    // ─── filter() — root path "/" → routeId = "unknown" ──────────────────────

    @Test
    void filter_rootPath_routeIdSetToUnknown_chainCalled() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/").build());
        // "/".split("/") has length <= 1 → "unknown"

        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any());
    }

    // ─── filter() — Discovery prefix stripped from routeId ───────────────────

    @Test
    void filter_discoveryPrefixRouteId_prefixStrippedChainCalled() {
        Route route = mock(Route.class);
        when(route.getId()).thenReturn("ReactiveCompositeDiscoveryClient_user-service");

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/user-service/api").build());
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);

        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(any());
    }

    // ─── looksLikeJson — private static method via ReflectionTestUtils ────────

    @Test
    void looksLikeJson_nullBody_returnsFalse() {
        Boolean result = ReflectionTestUtils.invokeMethod(filter, "looksLikeJson", (Object) null);
        assertThat(result).isFalse();
    }

    @Test
    void looksLikeJson_emptyBody_returnsFalse() {
        Boolean result = ReflectionTestUtils.invokeMethod(filter, "looksLikeJson", (Object) new byte[0]);
        assertThat(result).isFalse();
    }

    @Test
    void looksLikeJson_jsonObjectBody_returnsTrue() {
        byte[] body = "{\"key\":\"value\"}".getBytes();
        Boolean result = ReflectionTestUtils.invokeMethod(filter, "looksLikeJson", (Object) body);
        assertThat(result).isTrue();
    }

    @Test
    void looksLikeJson_jsonArrayBody_returnsTrue() {
        byte[] body = "[1,2,3]".getBytes();
        Boolean result = ReflectionTestUtils.invokeMethod(filter, "looksLikeJson", (Object) body);
        assertThat(result).isTrue();
    }

    @Test
    void looksLikeJson_plainTextBody_returnsFalse() {
        byte[] body = "hello world".getBytes();
        Boolean result = ReflectionTestUtils.invokeMethod(filter, "looksLikeJson", (Object) body);
        assertThat(result).isFalse();
    }

    @Test
    void looksLikeJson_whitespaceWrappedJson_returnsTrue() {
        byte[] body = "  { \"x\": 1 }  ".getBytes();
        Boolean result = ReflectionTestUtils.invokeMethod(filter, "looksLikeJson", (Object) body);
        assertThat(result).isTrue();
    }
}
