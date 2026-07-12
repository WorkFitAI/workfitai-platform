package org.workfitai.cvservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.workfitai.cvservice.client.dto.SectionExtractionResult;

/**
 * Regression coverage for the ollamaExtractedAt-set-on-DNS-failure bug: cv-service's
 * startup backfill fired before recommendation-engine's hostname was resolvable on the
 * Docker network, and {@link RecommendationEngineClient#extractSections} used to swallow
 * that connectivity failure into the same {@link SectionExtractionResult#notExtracted()}
 * sentinel as a genuine "Ollama ran, found nothing" result — so CvSectionEnrichmentService
 * permanently marked those CVs as attempted and they were never retried.
 *
 * Success-path tests run a real local HTTP server (JDK's built-in {@link HttpServer}) rather
 * than mocking WebClient — there's no MockWebServer/WireMock dependency in this project, and
 * a real socket round-trip exercises the actual client code instead of a stubbed shortcut.
 */
class RecommendationEngineClientTest {

    private HttpServer server;

    private RecommendationEngineClient clientWithUnreachableHost() {
        return new RecommendationEngineClient(
                WebClient.builder().build(),
                "http://recommendation-engine-does-not-exist:8000",
                2L, 2L);
    }

    private RecommendationEngineClient clientFor(HttpServer server) {
        return new RecommendationEngineClient(
                WebClient.builder().build(),
                "http://localhost:" + server.getAddress().getPort(),
                5L, 5L);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void extractSections_unreachableHost_throwsInsteadOfReturningNotExtractedSentinel() {
        RecommendationEngineClient client = clientWithUnreachableHost();

        assertThatThrownBy(() -> client.extractSections("some raw cv text"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void extractSections_blankText_returnsNotExtractedWithoutCallingOut() {
        RecommendationEngineClient client = clientWithUnreachableHost();

        SectionExtractionResult result = client.extractSections("   ");

        assertThat(result.isExtracted()).isFalse();
    }

    @Test
    void extractSections_successResponse_parsesExtractedResult() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/cv/extract-sections", exchange -> {
            String body = "{\"extracted\":true,\"summary\":\"S\",\"experience\":\"E\","
                    + "\"skills\":\"SK\",\"education\":\"ED\"}";
            respondJson(exchange, 200, body);
        });
        server.start();

        SectionExtractionResult result = clientFor(server).extractSections("raw text");

        assertThat(result.isExtracted()).isTrue();
        assertThat(result.getSummary()).isEqualTo("S");
        assertThat(result.getExperience()).isEqualTo("E");
        assertThat(result.getSkills()).isEqualTo("SK");
        assertThat(result.getEducation()).isEqualTo("ED");
    }

    @Test
    void isReachable_unreachableHost_returnsFalse() {
        // Backs the startup backfill's readiness wait: recommendation-engine loads ML
        // models on startup (minutes) while cv-service has none, so the one-shot backfill
        // polls this instead of firing immediately into a guaranteed connectivity failure.
        RecommendationEngineClient client = clientWithUnreachableHost();

        assertThat(client.isReachable()).isFalse();
    }

    @Test
    void isReachable_reachableHost_returnsTrue() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health", exchange -> exchange.sendResponseHeaders(200, -1));
        server.start();

        assertThat(clientFor(server).isReachable()).isTrue();
    }

    @Test
    void pushCvReferSnapshot_blankJobId_isNoOp() {
        assertThatCode(() -> clientWithUnreachableHost()
                .pushCvReferSnapshot("", "alice", "s", "e", "sk", "ed"))
                .doesNotThrowAnyException();
    }

    @Test
    void pushCvReferSnapshot_blankUsername_isNoOp() {
        assertThatCode(() -> clientWithUnreachableHost()
                .pushCvReferSnapshot("job-1", "  ", "s", "e", "sk", "ed"))
                .doesNotThrowAnyException();
    }

    @Test
    void pushCvReferSnapshot_success_completesWithoutError() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/cv-refer/snapshot", exchange -> exchange.sendResponseHeaders(200, -1));
        server.start();

        assertThatCode(() -> clientFor(server)
                .pushCvReferSnapshot("job-1", "alice", null, null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void pushCvReferSnapshot_unreachableHost_swallowsFailureInsteadOfThrowing() {
        // Unlike extractSections, this push is fire-and-forget best-effort — the caller
        // (CvSectionEnrichmentService) never retries it, so a failure here must never throw.
        assertThatCode(() -> clientWithUnreachableHost()
                .pushCvReferSnapshot("job-1", "alice", "s", "e", "sk", "ed"))
                .doesNotThrowAnyException();
    }

    private static void respondJson(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
