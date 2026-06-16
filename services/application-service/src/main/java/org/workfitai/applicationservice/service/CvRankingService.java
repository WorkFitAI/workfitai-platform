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

    private final RecommendationEngineClient recommendationEngineClient;
    private final ApplicationRepository applicationRepository;
    private final ApplicationMapper applicationMapper;
    private final AuditLogService auditLogService;

    /**
     * Calls the recommendation engine (may take 10-30 s), then merges the ranked
     * applicant list with full Application records.  Unranked applicants (those in
     * the DB but not returned by the AI) are appended at the end.
     */
    public CvRankingResultResponse rankCvsByJob(String jobId, String performedBy) {
        log.info("CV ranking requested: jobId={}, by={}", jobId, performedBy);

        // Fetch DB candidates first so we can fall back to unranked list if AI is unavailable
        List<Application> allApplications = applicationRepository
                .findByJobIdAndDeletedAtIsNull(jobId, Pageable.unpaged())
                .getContent();

        CvRankingResponse aiResult = null;
        try {
            aiResult = recommendationEngineClient.rankCvsByJob(jobId);
        } catch (Exception e) {
            log.warn("Recommendation engine unavailable for job {}, returning unranked candidates: {}", jobId, e.getMessage());
            auditLogService.logFailure(
                    "JOB", jobId, "CV_RANKING_FAILED", performedBy,
                    e.getMessage(),
                    Map.of("jobId", jobId)
            );
        }

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
                        .build());
                rankedUsernames.add(ra.getUsername());
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
}
