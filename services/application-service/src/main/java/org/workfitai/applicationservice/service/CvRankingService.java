package org.workfitai.applicationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.client.RecommendationEngineClient;
import org.workfitai.applicationservice.dto.response.CvRankingResponse;
import org.workfitai.applicationservice.dto.response.CvRankingResultResponse;
import org.workfitai.applicationservice.dto.response.RankedApplicationItem;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.repository.ApplicationRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates CV ranking by calling the recommendation engine and enriching
 * the result with full application records from MongoDB.
 *
 * Response order: ranked applicants first (in AI-ranked order), then unranked
 * applicants appended at the end without AI metadata.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CvRankingService {

    /** recommendation-engine returns this status (HTTP 202) while a cache miss is computing in the background. */
    private static final String STATUS_COMPUTING = "computing";
    /** Bounded retries on a "computing" response — caps total wait around the recommendation-engine's
     *  own CV_RANKING_MISS_RETRY_AFTER_SECONDS default (10s), matching the prior synchronous 10-30s budget. */
    private static final int MAX_RANKING_ATTEMPTS = 5;
    private static final int DEFAULT_RETRY_AFTER_SECONDS = 15;
    /** Author recorded on notes auto-created from the AI ranking explanation. */
    private static final String RANKING_NOTE_AUTHOR = "AI_RANKING";

    private final RecommendationEngineClient recommendationEngineClient;
    private final ApplicationRepository applicationRepository;
    private final ApplicationMapper applicationMapper;
    private final AuditLogService auditLogService;
    private final ApplicationNoteService applicationNoteService;

    /**
     * Calls the recommendation engine, then merges the ranked applicant list with full
     * Application records. Unranked applicants (those in the DB but not returned by the
     * AI) are appended at the end.
     */
    public CvRankingResultResponse rankCvsByJob(String jobId, String performedBy) {
        log.info("CV ranking requested: jobId={}, by={}", jobId, performedBy);

        // Fetch DB candidates first so we can fall back to unranked list if AI is unavailable
        List<Application> allApplications = applicationRepository
                .findByJobIdAndDeletedAtIsNull(jobId, Pageable.unpaged())
                .getContent();

        CvRankingResponse aiResult = fetchRankingWithRetry(jobId, performedBy);

        Map<String, Application> appByUsername = allApplications.stream()
                .collect(Collectors.toMap(Application::getUsername, a -> a, (a1, a2) -> a1));

        Set<String> rankedUsernames = new HashSet<>();
        List<RankedApplicationItem> items = new ArrayList<>();

        // 1. Ranked applicants — in AI-ranked order (skipped when AI is unavailable)
        if (aiResult != null && aiResult.getRankedApplicants() != null) {
            int rankPos = 1;
            for (CvRankingResponse.RankedApplicant ra : aiResult.getRankedApplicants()) {
                Application app = appByUsername.get(ra.getUsername());
                if (app == null) {
                    log.debug("Ranked username '{}' has no active application for job {}", ra.getUsername(), jobId);
                    continue;
                }
                items.add(RankedApplicationItem.builder()
                        .application(applicationMapper.toResponse(app))
                        .ranked(true)
                        .rank(rankPos++)
                        .score(ra.getScore())
                        .label(ra.getLabel())
                        .explanation(ra.getExplanation())
                        .similarityScore(ra.getSimilarityScore())
                        .crossScore(ra.getCrossScore())
                        .matchPoints(ra.getMatchPoints())
                        .missPoints(ra.getMissPoints())
                        .inputCoverage(ra.getInputCoverage())
                        .build());
                rankedUsernames.add(ra.getUsername());
                applicationNoteService.addNoteIfContentIsNew(app, ra.getExplanation(), RANKING_NOTE_AUTHOR);
                log.debug("CV ranking candidate: jobId={}, username={}, score={}, label={}, matchPoints={}, missPoints={}",
                        jobId, ra.getUsername(), ra.getScore(), ra.getLabel(), ra.getMatchPoints(), ra.getMissPoints());
            }
        }

        // 2. Unranked applicants — preserve DB insertion order
        for (Application app : allApplications) {
            if (!rankedUsernames.contains(app.getUsername())) {
                items.add(RankedApplicationItem.builder()
                        .application(applicationMapper.toResponse(app))
                        .ranked(false)
                        .build());
            }
        }

        int rankedCount = rankedUsernames.size();
        int unrankedCount = items.size() - rankedCount;
        double processingTimeMs = aiResult != null ? aiResult.getProcessingTimeMs() : 0;
        String jobOverview = aiResult != null ? aiResult.getJobOverview() : "";

        if (aiResult != null) {
            auditLogService.logAction(
                    "JOB", jobId, "CV_RANKING_COMPLETED", performedBy,
                    null, null,
                    Map.of(
                            "rankedCount", rankedCount,
                            "unrankedCount", unrankedCount,
                            "totalCandidates", items.size(),
                            "processingTimeMs", processingTimeMs
                    )
            );
            log.info("CV ranking completed: jobId={}, ranked={}, unranked={}, aiMs={}",
                    jobId, rankedCount, unrankedCount, processingTimeMs);
        } else {
            log.info("CV ranking degraded (AI unavailable): jobId={}, returning {} unranked candidates",
                    jobId, items.size());
        }

        return CvRankingResultResponse.builder()
                .jobId(jobId)
                .jobOverview(jobOverview)
                .totalCandidates(items.size())
                .rankedCount(rankedCount)
                .unrankedCount(unrankedCount)
                .processingTimeMs(processingTimeMs)
                .applications(items)
                .build();
    }

    /**
     * Calls recommendation-engine's rank-by-job endpoint, retrying on a "computing"
     * (cache miss, background compute in progress) response up to MAX_RANKING_ATTEMPTS
     * times. Returns null when the engine is unreachable or still computing after all
     * retries — callers fall back to an unranked list in that case.
     */
    private CvRankingResponse fetchRankingWithRetry(String jobId, String performedBy) {
        for (int attempt = 1; attempt <= MAX_RANKING_ATTEMPTS; attempt++) {
            CvRankingResponse result;
            try {
                result = recommendationEngineClient.rankCvsByJob(jobId);
            } catch (Exception e) {
                log.warn("Recommendation engine unavailable for job {}, returning unranked candidates: {}", jobId, e.getMessage());
                auditLogService.logFailure(
                        "JOB", jobId, "CV_RANKING_FAILED", performedBy,
                        e.getMessage(),
                        Map.of("jobId", jobId)
                );
                return null;
            }

            if (result == null || !STATUS_COMPUTING.equals(result.getStatus())) {
                return result;
            }

            int retryAfterSeconds = result.getRetryAfterSeconds() != null
                    ? result.getRetryAfterSeconds() : DEFAULT_RETRY_AFTER_SECONDS;
            log.info("CV ranking still computing for job {} (attempt {}/{}); retrying in {}s",
                    jobId, attempt, MAX_RANKING_ATTEMPTS, retryAfterSeconds);

            if (attempt < MAX_RANKING_ATTEMPTS) {
                sleepQuietly(retryAfterSeconds);
            }
        }

        log.info("CV ranking not ready after {} attempts for job {}; returning unranked candidates for now",
                MAX_RANKING_ATTEMPTS, jobId);
        return null;
    }

    private void sleepQuietly(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
