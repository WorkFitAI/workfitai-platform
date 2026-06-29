package org.workfitai.apigateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.workfitai.apigateway.service.impl.RedisOpaqueTokenService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RedisOpaqueTokenServiceTest {

    @Mock ReactiveStringRedisTemplate redis;
    @Mock ReactiveValueOperations<String, String> valueOps;

    RedisOpaqueTokenService service;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(redis.opsForValue()).thenReturn(valueOps);
        service = new RedisOpaqueTokenService(redis);
    }

    // ─── mint ─────────────────────────────────────────────────────────────────

    @Test
    void mint_returnsNonEmptyOpaqueToken() {
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(service.mint("header.payload.sig", "access"))
                .assertNext(opaque -> {
                    assertThat(opaque).isNotBlank();
                    assertThat(opaque).doesNotContain("-"); // UUID without dashes
                    assertThat(opaque).hasSize(32);
                })
                .verifyComplete();
    }

    @Test
    void mint_storesWithAccessKeyPrefix() {
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        // Verify key format via Redis call: "opaque:access:{opaque}"
        StepVerifier.create(service.mint("header.payload.sig", "access"))
                .assertNext(opaque -> assertThat(opaque).isNotEmpty())
                .verifyComplete();
    }

    @Test
    void mint_withValidJwtExpiry_usesTtlFromToken() {
        // Build a JWT with exp = now + 1 hour
        long expEpoch = Instant.now().plusSeconds(3600).getEpochSecond();
        String payload = "{\"sub\":\"alice\",\"exp\":" + expEpoch + "}";
        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes());
        String jwt = "eyJhbGciOiJSUzI1NiJ9." + payloadB64 + ".signature";

        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(service.mint(jwt, "access"))
                .assertNext(opaque -> assertThat(opaque).isNotEmpty())
                .verifyComplete();
    }

    @Test
    void mint_withMalformedJwt_fallsBackToDefault15MinTtl() {
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(service.mint("not-a-jwt", "access"))
                .assertNext(opaque -> assertThat(opaque).isNotEmpty())
                .verifyComplete();
    }

    @Test
    void mint_withJwtMissingExpClaim_fallsBackToDefault() {
        String payload = "{\"sub\":\"alice\"}"; // no exp
        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes());
        String jwt = "eyJhbGciOiJSUzI1NiJ9." + payloadB64 + ".sig";

        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(service.mint(jwt, "refresh"))
                .assertNext(opaque -> assertThat(opaque).isNotEmpty())
                .verifyComplete();
    }

    // ─── toJwt ────────────────────────────────────────────────────────────────

    @Test
    void toJwt_returnsJwt_whenFoundUnderAccessKey() {
        when(valueOps.get("opaque:access:some-opaque")).thenReturn(Mono.just("header.payload.sig"));
        when(valueOps.get("opaque:refresh:some-opaque")).thenReturn(Mono.empty());

        StepVerifier.create(service.toJwt("some-opaque"))
                .expectNext("header.payload.sig")
                .verifyComplete();
    }

    @Test
    void toJwt_returnsJwt_whenFoundUnderRefreshKey() {
        when(valueOps.get("opaque:access:refresh-only")).thenReturn(Mono.empty());
        when(valueOps.get("opaque:refresh:refresh-only")).thenReturn(Mono.just("refresh.payload.sig"));

        StepVerifier.create(service.toJwt("refresh-only"))
                .expectNext("refresh.payload.sig")
                .verifyComplete();
    }

    @Test
    void toJwt_returnsEmpty_whenNotFoundUnderEitherKey() {
        when(valueOps.get("opaque:access:unknown")).thenReturn(Mono.empty());
        when(valueOps.get("opaque:refresh:unknown")).thenReturn(Mono.empty());

        StepVerifier.create(service.toJwt("unknown"))
                .verifyComplete();
    }

    // ─── revokeAll ────────────────────────────────────────────────────────────

    @Test
    void revokeAll_deletesAccessAndRefreshKeys() {
        when(redis.delete("opaque:access:tok", "opaque:refresh:tok")).thenReturn(Mono.just(2L));

        StepVerifier.create(service.revokeAll("tok"))
                .expectNext(2L)
                .verifyComplete();
    }

    @Test
    void revokeAll_returns0_whenNeitherKeyExists() {
        when(redis.delete("opaque:access:missing", "opaque:refresh:missing"))
                .thenReturn(Mono.just(0L));

        StepVerifier.create(service.revokeAll("missing"))
                .expectNext(0L)
                .verifyComplete();
    }
}
