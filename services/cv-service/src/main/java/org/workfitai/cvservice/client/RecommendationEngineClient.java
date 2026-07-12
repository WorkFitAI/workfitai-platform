package org.workfitai.cvservice.client;

import java.time.Duration;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.workfitai.cvservice.client.dto.SectionExtractionResult;

/**
 * Client for recommendation-engine's internal (Docker-network only) endpoints.
 *
 * Used by the async CV section-enrichment step to (1) extract resume sections via
 * Ollama and (2) push corrected sections into recommendation-engine's CvReferStore
 * for application-snapshot CVs.
 *
 * {@link #extractSections} deliberately does NOT swallow connectivity failures
 * (DNS/timeout/non-2xx/etc.) — those propagate to the caller so it can tell a
 * genuine "Ollama ran, nothing usable" result (safe to mark as attempted) apart
 * from "never actually reached recommendation-engine" (must NOT be marked as
 * attempted, so the next startup backfill retries it). {@link #pushCvReferSnapshot}
 * has no such retry path, so it stays best-effort and swallows its own failures.
 */
@Slf4j
@Component
public class RecommendationEngineClient {

    private final WebClient webClient;
    private final String baseUrl;
    private final Duration timeout;
    private final Duration healthCheckTimeout;

    public RecommendationEngineClient(
            WebClient webClient,
            @Value("${service.recommendation-engine.url:http://recommendation-engine:8000}") String baseUrl,
            @Value("${cv.ollama-extraction.timeout-seconds:60}") long timeoutSeconds,
            @Value("${cv.ollama-extraction.health-check-timeout-seconds:5}") long healthCheckTimeoutSeconds) {
        this.webClient = webClient;
        this.baseUrl = baseUrl;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.healthCheckTimeout = Duration.ofSeconds(healthCheckTimeoutSeconds);
    }

    /**
     * Extract {summary, experience, skills, education} from raw CV text via Ollama.
     *
     * Returns {@link SectionExtractionResult#notExtracted()} only when recommendation-engine
     * actually responded (Ollama ran but found nothing usable). On any failure to complete
     * the round-trip (DNS/connection/timeout/non-2xx) this THROWS instead of swallowing —
     * the caller must NOT treat an unreachable recommendation-engine as "attempted", or
     * affected CVs would never be retried by the next startup backfill.
     */
    public SectionExtractionResult extractSections(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return SectionExtractionResult.notExtracted();
        }
        SectionExtractionResult result = webClient.post()
                .uri(baseUrl + "/internal/cv/extract-sections")
                .bodyValue(Map.of("text", rawText, "doc_type", "RESUME"))
                .retrieve()
                .bodyToMono(SectionExtractionResult.class)
                .block(timeout);
        return result != null ? result : SectionExtractionResult.notExtracted();
    }

    /**
     * Lightweight readiness probe against recommendation-engine's own {@code /health}
     * endpoint. Uvicorn doesn't accept connections until recommendation-engine's startup
     * sequence (including its ML model loads, which can take minutes) has completed, so a
     * successful response here means it's actually ready to serve {@link #extractSections}
     * — not just that its container process has started.
     *
     * Used by the startup backfill to wait out the very real race where cv-service (no
     * heavy model loading) becomes ready well before recommendation-engine does.
     */
    public boolean isReachable() {
        try {
            webClient.get()
                    .uri(baseUrl + "/health")
                    .retrieve()
                    .toBodilessEntity()
                    .block(healthCheckTimeout);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Push corrected CV sections into recommendation-engine's CvReferStore for an
     * application-snapshot CV, so HR CV-ranking for the job reflects them immediately.
     * Best-effort: failures are logged and swallowed.
     */
    public void pushCvReferSnapshot(
            String jobId, String username, String summary, String experience, String skills, String education) {
        if (jobId == null || jobId.isBlank() || username == null || username.isBlank()) {
            return;
        }
        try {
            webClient.post()
                    .uri(baseUrl + "/internal/cv-refer/snapshot")
                    .bodyValue(Map.of(
                            "job_id", jobId,
                            "username", username,
                            "summary", summary != null ? summary : "",
                            "experience", experience != null ? experience : "",
                            "skills", skills != null ? skills : "",
                            "education", education != null ? education : ""))
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block(timeout);
        } catch (Exception e) {
            log.warn("recommendation-engine cv-refer snapshot push failed for jobId={} username={}: {}",
                    jobId, username, e.getMessage());
        }
    }
}
