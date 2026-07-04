package org.workfitai.apigateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.workfitai.apigateway.service.IOpaqueTokenService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class OpaqueToJwtWebFilterTest {

    @Mock IOpaqueTokenService opaqueTokenService;
    @Mock WebFilterChain chain;

    OpaqueToJwtWebFilter filter;

    @BeforeEach
    void setUp() {
        filter = new OpaqueToJwtWebFilter(opaqueTokenService);
    }

    // ─── 5a-1: No Authorization header ───────────────────────────────────────

    @Test
    void filter_noAuthorizationHeader_chainCalledWithoutServiceInteraction() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/test").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(opaqueTokenService);
    }

    // ─── 5a-2: Bearer prefix present but token blank after strip ─────────────

    @Test
    void filter_bearerWithBlankToken_chainCalledWithoutServiceInteraction() {
        // "Bearer " + whitespace → substring(7) = whitespace → hasText = false
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/test")
                        .header("Authorization", "Bearer   ")
                        .build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(opaqueTokenService);
    }

    // ─── 5a-3: Token already a JWT (2 dots, length > 50) ─────────────────────

    @Test
    void filter_tokenAlreadyJwt_chainCalledUnchangedNoServiceInteraction() {
        // 2 dots + length > 50 satisfies the JWT detection heuristic
        String jwt = "a." + "b." + "c".repeat(48);
        assertThat(jwt.chars().filter(ch -> ch == '.').count()).isEqualTo(2);
        assertThat(jwt.length()).isGreaterThan(50);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/test")
                        .header("Authorization", "Bearer " + jwt)
                        .build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(opaqueTokenService);
    }

    // ─── 5a-4: Opaque token, service returns JWT → mutated headers ───────────

    @Test
    void filter_opaqueToken_serviceReturnsJwt_chainCalledWithMutatedAuthorizationAndXTokenSourceHeaders() {
        String opaque = "opaque-token-abc";
        String jwt = "eyJ.pay.sig";

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/test")
                        .header("Authorization", "Bearer " + opaque)
                        .build());

        when(opaqueTokenService.toJwt(opaque)).thenReturn(Mono.just(jwt));

        ArgumentCaptor<ServerWebExchange> captor = ArgumentCaptor.forClass(ServerWebExchange.class);
        when(chain.filter(captor.capture())).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(opaqueTokenService).toJwt(opaque);
        ServerWebExchange captured = captor.getValue();
        assertThat(captured.getRequest().getHeaders().getFirst("Authorization"))
                .isEqualTo("Bearer " + jwt);
        assertThat(captured.getRequest().getHeaders().getFirst("X-Token-Source"))
                .isEqualTo("opaque");
        assertThat(captured.getRequest().getHeaders().getFirst("X-Original-Opaque"))
                .isEqualTo(opaque);
    }

    // ─── 5a-5: Opaque token, service returns Mono.empty() ────────────────────

    @Test
    void filter_opaqueToken_serviceReturnsEmpty_chainCalledWithOriginalRequest() {
        String opaque = "opaque-no-jwt";

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/test")
                        .header("Authorization", "Bearer " + opaque)
                        .build());

        when(opaqueTokenService.toJwt(opaque)).thenReturn(Mono.empty());
        // defaultIfEmpty("") → flatMap sees blank jwt → chain.filter(original exchange)
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    // ─── 5a-6: Opaque token, service throws → fail-open ──────────────────────

    @Test
    void filter_opaqueToken_serviceThrows_failOpenChainCalledWithOriginalRequest() {
        String opaque = "opaque-error";

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/test")
                        .header("Authorization", "Bearer " + opaque)
                        .build());

        when(opaqueTokenService.toJwt(opaque))
                .thenReturn(Mono.error(new RuntimeException("Redis connection error")));
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
    }

    // ─── 5a-7: Authorization without Bearer prefix ────────────────────────────

    @Test
    void filter_authHeaderWithoutBearerPrefix_serviceCalledWithFullHeaderValue() {
        String opaque = "raw-opaque-token";

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/test")
                        .header("Authorization", opaque)
                        .build());

        when(opaqueTokenService.toJwt(opaque)).thenReturn(Mono.empty());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(opaqueTokenService).toJwt(opaque);
    }

    // ─── Edge: JWT token with only 1 dot is treated as opaque ─────────────────

    @Test
    void filter_tokenWithOneDot_treatedAsOpaqueServiceCalled() {
        String oneDoToken = "header.payload"; // 1 dot → not JWT

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/test")
                        .header("Authorization", "Bearer " + oneDoToken)
                        .build());

        when(opaqueTokenService.toJwt(oneDoToken)).thenReturn(Mono.empty());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(opaqueTokenService).toJwt(oneDoToken);
    }

    // ─── Edge: JWT with 2 dots but length <= 50 treated as opaque ─────────────

    @Test
    void filter_twoDotTokenShortLength_treatedAsOpaqueServiceCalled() {
        String shortJwtLike = "a.b.c"; // 2 dots but length = 5, not > 50 → opaque

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/test")
                        .header("Authorization", "Bearer " + shortJwtLike)
                        .build());

        when(opaqueTokenService.toJwt(shortJwtLike)).thenReturn(Mono.empty());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filter.filter(exchange, chain))
                .verifyComplete();

        verify(opaqueTokenService).toJwt(shortJwtLike);
    }
}
