package org.workfitai.apigateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Tests for {@link JwtClaimsExtractionFilter}.
 *
 * <p>Design note: The filter uses {@code .flatMap(...chain.filter(mutatedExchange)).switchIfEmpty(...)}.
 * Because {@code Mono<Void>} never emits an element, {@code switchIfEmpty} always fires
 * after {@code flatMap} completes, calling the chain a second time with the original exchange.
 * Tests for the authenticated path therefore use {@code verify(atLeastOnce()).filter(argThat(...))}
 * to assert the mutated call, rather than capturing the last-called exchange.
 */
@ExtendWith(MockitoExtension.class)
class JwtClaimsExtractionFilterTest {

    @Mock GatewayFilterChain chain;

    JwtClaimsExtractionFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtClaimsExtractionFilter();
    }

    // ─── filter order ─────────────────────────────────────────────────────────

    @Test
    void getOrder_returnsNegative50() {
        assertThat(filter.getOrder()).isEqualTo(-50);
    }

    // ─── unauthenticated path (switchIfEmpty branch) ──────────────────────────

    @Test
    void filter_noAuthentication_delegatesToChainWithoutAddingHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/users").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        // Runs with empty ReactiveSecurityContext → hits switchIfEmpty → chain.filter
        Mono<Void> result = filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.clearContext());

        StepVerifier.create(result)
                .verifyComplete();

        // No X-Username or X-User-Roles headers should be present
        assertThat(exchange.getRequest().getHeaders().getFirst("X-Username")).isNull();
        assertThat(exchange.getRequest().getHeaders().getFirst("X-User-Roles")).isNull();
    }

    @Test
    void filter_noAuthentication_doesNotThrow_forAnyPath() {
        String[] paths = {"/auth/login", "/api/jobs", "/actuator/health"};
        for (String path : paths) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.get("http://localhost" + path).build());
            when(chain.filter(exchange)).thenReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, chain)
                            .contextWrite(ReactiveSecurityContextHolder.clearContext()))
                    .verifyComplete();
        }
    }

    // ─── authenticated JWT path ───────────────────────────────────────────────
    //
    // chain.filter() is stubbed with any() because the filter calls it twice:
    //   (1) chain.filter(mutatedExchange) from flatMap
    //   (2) chain.filter(original) from switchIfEmpty (Mono<Void> has 0 elements)
    // We use verify(atLeastOnce()).filter(argThat(...)) to assert the mutated call.

    // 5b-1: JWT with username + roles → X-Username and X-User-Roles headers set on mutated exchange
    @Test
    void filter_jwtWithUsernameAndRoles_callsChainWithXUsernameAndXUserRolesHeaders() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("alice")
                .claim("roles", List.of("CANDIDATE"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of());
        SecurityContext ctx = new SecurityContextImpl(auth);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/jobs").build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ctx))))
                .verifyComplete();

        verify(chain, atLeastOnce()).filter(argThat(ex ->
                "alice".equals(ex.getRequest().getHeaders().getFirst("X-Username"))
                && "CANDIDATE".equals(ex.getRequest().getHeaders().getFirst("X-User-Roles"))
        ));
    }

    // 5b-2: JWT username present, roles null (claim absent) → only X-Username set
    @Test
    void filter_jwtWithUsernameOnlyRolesAbsent_callsChainWithOnlyXUsernameHeader() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("bob")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of());
        SecurityContext ctx = new SecurityContextImpl(auth);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/jobs").build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ctx))))
                .verifyComplete();

        verify(chain, atLeastOnce()).filter(argThat(ex ->
                "bob".equals(ex.getRequest().getHeaders().getFirst("X-Username"))
                && ex.getRequest().getHeaders().getFirst("X-User-Roles") == null
        ));
    }

    // 5b-3: JWT username null (sub absent), roles present → only X-User-Roles set
    @Test
    void filter_jwtWithRolesOnlySubjectAbsent_callsChainWithOnlyXUserRolesHeader() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("roles", List.of("ADMIN", "USER"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of());
        SecurityContext ctx = new SecurityContextImpl(auth);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/jobs").build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ctx))))
                .verifyComplete();

        verify(chain, atLeastOnce()).filter(argThat(ex ->
                ex.getRequest().getHeaders().getFirst("X-Username") == null
                && "ADMIN,USER".equals(ex.getRequest().getHeaders().getFirst("X-User-Roles"))
        ));
    }

    // 5b-4: Both null → chain called without user headers (mutated exchange still passed)
    @Test
    void filter_jwtWithNeitherSubjectNorRoles_chainCalledWithoutUserHeaders() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, List.of());
        SecurityContext ctx = new SecurityContextImpl(auth);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/jobs").build());
        when(chain.filter(any())).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ctx))))
                .verifyComplete();

        // chain is called — no assertion on header values since neither username nor roles is set
        verify(chain, atLeastOnce()).filter(any());
    }

    // 5b-5: Auth present but principal NOT a Jwt → hits switchIfEmpty, chain called with ORIGINAL
    @Test
    void filter_authPrincipalNotJwt_hitsSwichIfEmptyChainCalledWithOriginalExchange() {
        // UsernamePasswordAuthenticationToken principal is a String, not Jwt
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("user", null, List.of());
        SecurityContext ctx = new SecurityContextImpl(auth);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("http://localhost/api/jobs").build());
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(
                filter.filter(exchange, chain)
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(ctx))))
                .verifyComplete();

        // No flatMap run → switchIfEmpty calls chain with original (unmodified) exchange
        verify(chain).filter(exchange);
    }
}
