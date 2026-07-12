package org.workfitai.cvservice.service.enrichment;

import java.io.InputStream;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.workfitai.cvservice.client.RecommendationEngineClient;
import org.workfitai.cvservice.model.CV;
import org.workfitai.cvservice.repository.CVRepository;
import org.workfitai.cvservice.service.shared.FileService;
import org.workfitai.cvservice.service.strategy.UploadCvStrategy;

/**
 * One-shot Ollama section-extraction backfill for CVs that have never had an
 * attempt (CV.ollamaExtractedAt is null) — runs ONLY once per process start
 * (on {@link ApplicationReadyEvent}, i.e. every restart/rebuild of cv-service),
 * not on a recurring schedule.
 *
 * cv-service has no heavy model loading and typically becomes ready well before
 * recommendation-engine (which loads several ML models on startup — can take
 * minutes). Since this pass is one-shot, firing it immediately would burn the
 * only per-restart attempt on CVs that can never actually be reached, so it
 * first waits (bounded, see {@code ready-wait-seconds}) for recommendation-engine
 * to report itself reachable via {@link RecommendationEngineClient#isReachable()}.
 *
 * For each candidate CV it re-downloads the stored PDF, re-extracts raw text,
 * and submits it through the same {@link CvSectionEnrichmentService} path a
 * fresh upload uses — so a CV only ever "counts" as attempted once that path
 * actually completes (see CV.ollamaExtractedAt javadoc). A single startup pass
 * only claims a bounded batch; any CVs left over (either because there were
 * more than the batch size, or because the bounded enrichment executor dropped
 * some under saturation, or because recommendation-engine never became reachable)
 * are picked up by the NEXT restart's pass — this makes a large backlog self-heal
 * across restarts without needing a recurring job.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CvOllamaBackfillRunner {

    private final CVRepository repository;
    private final CvSectionEnrichmentService enrichmentService;
    private final FileService fileService;
    private final UploadCvStrategy uploadCvStrategy;
    private final RecommendationEngineClient recommendationEngineClient;

    @Value("${cv.ollama-extraction.enabled:true}")
    private boolean enabled;

    @Value("${cv.ollama-extraction.backfill.batch-size:50}")
    private int batchSize;

    @Value("${cv.ollama-extraction.backfill.ready-wait-seconds:240}")
    private int readyWaitSeconds;

    @Value("${cv.ollama-extraction.backfill.ready-poll-interval-ms:5000}")
    private long readyPollIntervalMs;

    // Own dedicated single-thread executor (NOT cvEnrichmentExecutor) — this loop only
    // orchestrates (sequential MinIO download + text extraction per CV); the actual
    // Ollama round-trip it triggers via enrichAsync/enrichSnapshotAsync still goes
    // through cvEnrichmentExecutor. See AsyncConfig's javadoc for why these are split.
    @Async("cvBackfillExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void backfillUnattemptedCvs() {
        if (!enabled) {
            log.info("Ollama CV backfill skipped on startup (CV_OLLAMA_EXTRACTION_ENABLED=false)");
            return;
        }

        if (!awaitRecommendationEngineReady()) {
            log.warn("Ollama CV backfill: recommendation-engine not reachable after {}s — skipping this "
                    + "startup pass (unattempted CVs stay null and will be retried on next restart)",
                    readyWaitSeconds);
            return;
        }

        List<CV> candidates = repository.findByOllamaExtractedAtIsNullAndIsExistTrueAndObjectNameIsNotNull(
                PageRequest.of(0, batchSize));
        if (candidates.isEmpty()) {
            log.info("Ollama CV backfill: no unattempted CVs found on startup");
            return;
        }

        log.info("Ollama CV backfill: submitting {} unattempted CV(s) on startup", candidates.size());
        for (CV cv : candidates) {
            submitOne(cv);
        }
    }

    /**
     * Poll recommendation-engine's health endpoint until reachable or {@code readyWaitSeconds}
     * elapses. Runs on this class's own dedicated single-thread executor, so blocking here
     * doesn't hold up app startup or any other request thread.
     */
    private boolean awaitRecommendationEngineReady() {
        long deadline = System.currentTimeMillis() + readyWaitSeconds * 1000L;
        while (true) {
            if (recommendationEngineClient.isReachable()) {
                return true;
            }
            if (System.currentTimeMillis() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(readyPollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private void submitOne(CV cv) {
        if (cv.getObjectName() == null) {
            // Template CVs have no PDF to re-extract text from — nothing to attempt.
            return;
        }
        try {
            String rawText;
            try (InputStream in = fileService.downloadCV(cv.getObjectName())) {
                rawText = uploadCvStrategy.extractRawText(in);
            }

            if (cv.getApplicationId() != null && cv.getJobId() != null) {
                enrichmentService.enrichSnapshotAsync(cv.getCvId(), cv.getJobId(), cv.getBelongTo(), rawText);
            } else {
                enrichmentService.enrichAsync(cv.getCvId(), rawText);
            }
        } catch (Exception e) {
            // Best-effort per CV — e.g. object deleted from MinIO, scanned/OCR-only PDF
            // rejected by PdfComplexityDetector. Left with ollamaExtractedAt still null,
            // so it will simply be retried on a future restart.
            log.warn("Ollama CV backfill: failed to submit cvId={}: {}", cv.getCvId(), e.getMessage());
        }
    }
}
