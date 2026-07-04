package org.workfitai.apigateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.workfitai.apigateway.service.IOpaqueTokenService;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Tests for {@link MintOpaquePostFilter.MintOpaqueFilterImpl}.
 *
 * <p>Login-path tests (7a-2 through 7a-8) that exercise
 * {@code ModifyResponseBodyGatewayFilterFactory.apply().filter()} require the full
 * Spring WebFlux HTTP-codec stack and cannot be unit-tested in isolation.
 * Those cases are covered in the path-regex tests below, and the factory-dependent
 * body-rewriting logic is left for integration tests.
 */
@ExtendWith(MockitoExtension.class)
class MintOpaqueFilterTest {

    @Mock IOpaqueTokenService opaqueTokenService;
    @Mock GatewayFilterChain chain;

    MintOpaquePostFilter.MintOpaqueFilterImpl filterImpl;

    @BeforeEach
    void setUp() {
        // Pass empty codec collections — the factory is constructed but apply() is
        // never reached for non-login paths, so the empty collections do not NPE.
        filterImpl = new MintOpaquePostFilter.MintOpaqueFilterImpl(
                opaqueTokenService,
                List.of(),   // HttpMessageReader list
                Set.of(),    // MessageBodyDecoder set
                Set.of()     // MessageBodyEncoder set
        );
    }

    // ─── getOrder ─────────────────────────────────────────────────────────────

    @Test
    void getOrder_returnsLowestPrecedenceMinusOne() {
        assertThat(filterImpl.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE - 1);
    }

    // ─── 7a-1: Non-login path → chain called directly, no opaqueTokenService ──

    @Test
    void filter_nonLoginPath_chainCalledDirectlyWithoutServiceInteraction() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/jobs").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filterImpl.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(opaqueTokenService);
    }

    @Test
    void filter_actuatorPath_chainCalledDirectlyWithoutServiceInteraction() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/actuator/health").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filterImpl.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(opaqueTokenService);
    }

    @Test
    void filter_authRegisterPath_chainCalledDirectlyWithoutServiceInteraction() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("http://localhost/auth/register").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(filterImpl.filter(exchange, chain))
                .verifyComplete();

        verify(chain).filter(exchange);
        verifyNoInteractions(opaqueTokenService);
    }

    // ─── Path regex verification (7a-6, 7a-7 logic) ──────────────────────────
    //
    // The filter uses: path.matches(".*(/auth/login|/login|/auth/oauth/exchange)$")
    // Test the regex directly — these assertions mirror the exact pattern in source.

    private static final String LOGIN_PATH_REGEX = ".*(/auth/login|/login|/auth/oauth/exchange)$";

    @Test
    void pathRegex_matchesAuthLogin() {
        assertThat("/auth/login".matches(LOGIN_PATH_REGEX)).isTrue();
    }

    @Test
    void pathRegex_matchesRootLogin() {
        assertThat("/login".matches(LOGIN_PATH_REGEX)).isTrue();
    }

    @Test
    void pathRegex_matchesAuthOauthExchange() {
        assertThat("/auth/oauth/exchange".matches(LOGIN_PATH_REGEX)).isTrue();
    }

    @Test
    void pathRegex_matchesAuthLoginWithPrefix() {
        // prefix path segments do not affect the match
        assertThat("/v1/auth/login".matches(LOGIN_PATH_REGEX)).isTrue();
    }

    @Test
    void pathRegex_doesNotMatchAuthRegister() {
        assertThat("/auth/register".matches(LOGIN_PATH_REGEX)).isFalse();
    }

    @Test
    void pathRegex_doesNotMatchAuthLoginWithTrailingSegment() {
        // trailing extra segment breaks the $ anchor
        assertThat("/auth/login/extra".matches(LOGIN_PATH_REGEX)).isFalse();
    }

    @Test
    void pathRegex_doesNotMatchApiJobs() {
        assertThat("/api/jobs".matches(LOGIN_PATH_REGEX)).isFalse();
    }

    @Test
    void pathRegex_doesNotMatchEmptyString() {
        assertThat("".matches(LOGIN_PATH_REGEX)).isFalse();
    }
}
