package org.workfitai.cvservice.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 */
class RecommendationEngineClientTest {

    private RecommendationEngineClient clientWithUnreachableHost() {
        return new RecommendationEngineClient(
                WebClient.builder().build(),
                "http://recommendation-engine-does-not-exist:8000",
                2L, 2L);
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
    void isReachable_unreachableHost_returnsFalse() {
        // Backs the startup backfill's readiness wait: recommendation-engine loads ML
        // models on startup (minutes) while cv-service has none, so the one-shot backfill
        // polls this instead of firing immediately into a guaranteed connectivity failure.
        RecommendationEngineClient client = clientWithUnreachableHost();

        assertThat(client.isReachable()).isFalse();
    }
}
