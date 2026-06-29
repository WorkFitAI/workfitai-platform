package org.workfitai.apigateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.workfitai.apigateway.config.SecurityWebFluxTestConfig;
import org.workfitai.apigateway.service.IOpaqueTokenService;

@WebFluxTest(FallbackController.class)
@Import(SecurityWebFluxTestConfig.class)
class FallbackControllerTest {

    @Autowired WebTestClient client;
    @MockBean IOpaqueTokenService opaqueTokenService;

    // ─── genericFallback ──────────────────────────────────────────────────────

    @Test
    void genericFallback_returns503WithServiceName() {
        client.get().uri("/fallback/auth")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.service").isEqualTo("auth")
                .jsonPath("$.status").isEqualTo(503);
    }

    @Test
    void genericFallback_includesRequestId_whenHeaderProvided() {
        client.get().uri("/fallback/job")
                .header("X-Request-Id", "req-abc-123")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.requestId").isEqualTo("req-abc-123");
    }

    @Test
    void genericFallback_setsRequestIdToNA_whenHeaderAbsent() {
        client.get().uri("/fallback/user")
                .exchange()
                .expectBody()
                .jsonPath("$.requestId").isEqualTo("N/A");
    }

    // ─── auth fallbacks ───────────────────────────────────────────────────────

    @Test
    void authLoginFallback_returns503() {
        client.post().uri("/fallback/auth/login")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.service").isEqualTo("auth");
    }

    @Test
    void authRegisterFallback_returns503() {
        client.post().uri("/fallback/auth/register")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    // ─── user fallbacks ───────────────────────────────────────────────────────

    @Test
    void userProfileFallback_returns503() {
        client.get().uri("/fallback/user/profile")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.service").isEqualTo("user");
    }

    @Test
    void userProfileUpdateFallback_returns503() {
        client.put().uri("/fallback/user/profile")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    // ─── job fallback ─────────────────────────────────────────────────────────

    @Test
    void jobServiceFallback_returns503() {
        client.get().uri("/fallback/job/listings")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.service").isEqualTo("job");
    }

    // ─── cv fallback ──────────────────────────────────────────────────────────

    @Test
    void cvUploadFallback_returns503() {
        client.post().uri("/fallback/cv/upload")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.service").isEqualTo("cv");
    }

    // ─── notification fallback — returns 200 (non-critical) ──────────────────

    @Test
    void notificationFallback_returns200() {
        client.get().uri("/fallback/notification/list")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service").isEqualTo("notification");
    }

    // ─── health fallback ──────────────────────────────────────────────────────

    @Test
    void healthFallback_returns503WithStatusDown() {
        client.get().uri("/fallback/health")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.status").isEqualTo("DOWN");
    }
}
