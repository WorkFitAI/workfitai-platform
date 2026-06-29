package org.workfitai.applicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.workfitai.applicationservice.client.RecommendationEngineClient;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.CvRankingResponse;
import org.workfitai.applicationservice.dto.response.CvRankingResultResponse;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;

@ExtendWith(MockitoExtension.class)
class CvRankingServiceTest {

    @Mock RecommendationEngineClient recommendationEngineClient;
    @Mock ApplicationRepository applicationRepository;
    @Mock ApplicationMapper applicationMapper;
    @Mock AuditLogService auditLogService;

    @InjectMocks CvRankingService service;

    private Application buildApp(String username) {
        return Application.builder()
                .id("app-" + username)
                .username(username)
                .status(ApplicationStatus.APPLIED)
                .build();
    }

    // ─── rankCvsByJob ─────────────────────────────────────────────────────────

    @Test
    void rankCvsByJob_aiReturnsRankedList_mergesRankedFirst() {
        Application app1 = buildApp("user1");
        Application app2 = buildApp("user2");

        when(applicationRepository.findByJobIdAndDeletedAtIsNull(eq("job-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(app1, app2)));

        CvRankingResponse.RankedApplicant ra = CvRankingResponse.RankedApplicant.builder()
                .username("user1").score(0.95).label("EXCELLENT").explanation("Great CV")
                .similarityScore(0.9).crossScore(0.8).build();
        CvRankingResponse aiResult = CvRankingResponse.builder()
                .jobId("job-1")
                .rankedApplicants(List.of(ra))
                .processingTimeMs(120.0)
                .jobOverview("Senior Java Developer")
                .build();

        when(recommendationEngineClient.rankCvsByJob("job-1")).thenReturn(aiResult);
        when(applicationMapper.toResponse(any())).thenReturn(new ApplicationResponse());

        CvRankingResultResponse result = service.rankCvsByJob("job-1", "hr1");

        assertThat(result.getRankedCount()).isEqualTo(1);
        assertThat(result.getUnrankedCount()).isEqualTo(1);
        assertThat(result.getTotalCandidates()).isEqualTo(2);
        assertThat(result.getJobOverview()).isEqualTo("Senior Java Developer");
        assertThat(result.getApplications().get(0).isRanked()).isTrue();
        assertThat(result.getApplications().get(1).isRanked()).isFalse();
    }

    @Test
    void rankCvsByJob_aiThrowsException_returnsAllUnranked() {
        Application app = buildApp("user1");
        when(applicationRepository.findByJobIdAndDeletedAtIsNull(eq("job-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(app)));
        when(recommendationEngineClient.rankCvsByJob("job-1"))
                .thenThrow(new RuntimeException("AI service down"));
        when(applicationMapper.toResponse(app)).thenReturn(new ApplicationResponse());

        CvRankingResultResponse result = service.rankCvsByJob("job-1", "hr1");

        assertThat(result.getRankedCount()).isEqualTo(0);
        assertThat(result.getUnrankedCount()).isEqualTo(1);
        assertThat(result.getApplications().get(0).isRanked()).isFalse();
    }

    @Test
    void rankCvsByJob_aiReturnsNull_returnsAllUnranked() {
        Application app = buildApp("user1");
        when(applicationRepository.findByJobIdAndDeletedAtIsNull(eq("job-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(app)));
        when(recommendationEngineClient.rankCvsByJob("job-1")).thenReturn(null);
        when(applicationMapper.toResponse(app)).thenReturn(new ApplicationResponse());

        CvRankingResultResponse result = service.rankCvsByJob("job-1", "hr1");

        assertThat(result.getRankedCount()).isEqualTo(0);
        assertThat(result.getUnrankedCount()).isEqualTo(1);
    }

    @Test
    void rankCvsByJob_rankedUsernameNotInDb_skipsItAndCountsCorrectly() {
        Application app = buildApp("user1");
        when(applicationRepository.findByJobIdAndDeletedAtIsNull(eq("job-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(app)));

        // AI returns ranking for "ghost" who has no active application
        CvRankingResponse.RankedApplicant ghost = CvRankingResponse.RankedApplicant.builder()
                .username("ghost").score(0.9).build();
        CvRankingResponse aiResult = CvRankingResponse.builder()
                .rankedApplicants(List.of(ghost)).build();

        when(recommendationEngineClient.rankCvsByJob("job-1")).thenReturn(aiResult);
        when(applicationMapper.toResponse(app)).thenReturn(new ApplicationResponse());

        CvRankingResultResponse result = service.rankCvsByJob("job-1", "hr1");

        assertThat(result.getRankedCount()).isEqualTo(0); // ghost skipped
        assertThat(result.getUnrankedCount()).isEqualTo(1); // user1 is unranked
    }

    @Test
    void rankCvsByJob_noApplicationsInDb_returnsEmptyResult() {
        when(applicationRepository.findByJobIdAndDeletedAtIsNull(eq("job-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        when(recommendationEngineClient.rankCvsByJob("job-1")).thenReturn(null);

        CvRankingResultResponse result = service.rankCvsByJob("job-1", "hr1");

        assertThat(result.getTotalCandidates()).isEqualTo(0);
        assertThat(result.getApplications()).isEmpty();
    }

    @Test
    void rankCvsByJob_aiReturnsNullRankedApplicants_treatsAsEmpty() {
        Application app = buildApp("user1");
        when(applicationRepository.findByJobIdAndDeletedAtIsNull(eq("job-1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(app)));

        CvRankingResponse aiResult = CvRankingResponse.builder()
                .rankedApplicants(null).processingTimeMs(50.0).build();

        when(recommendationEngineClient.rankCvsByJob("job-1")).thenReturn(aiResult);
        when(applicationMapper.toResponse(app)).thenReturn(new ApplicationResponse());

        CvRankingResultResponse result = service.rankCvsByJob("job-1", "hr1");

        assertThat(result.getUnrankedCount()).isEqualTo(1);
    }
}
