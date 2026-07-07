package org.workfitai.applicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.workfitai.applicationservice.dto.response.ManagerStatsResponse;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ManagerStatsServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock MongoTemplate mongoTemplate;

    @InjectMocks ManagerStatsService service;

    private AggregationResults emptyAgg() {
        return new AggregationResults<>(Collections.emptyList(), new Document());
    }

    private void stubCommonMocks(String companyId) {
        when(applicationRepository.countByCompanyIdAndDeletedAtIsNull(companyId)).thenReturn(0L);
        when(mongoTemplate.aggregate(any(Aggregation.class), anyString(), any())).thenReturn(emptyAgg());
        when(applicationRepository.findByCompanyIdAndDeletedAtIsNull(companyId)).thenReturn(Collections.emptyList());
        when(applicationRepository.countByCompanyIdAndStatusInAndDeletedAtIsNullAndUpdatedAtBefore(
                anyString(), anyList(), any(Instant.class))).thenReturn(0L);
        when(applicationRepository.countByCompanyIdAndStatusAndDeletedAtIsNull(anyString(), any()))
                .thenReturn(0L);
    }

    @Test
    void getManagerStats_emptyCompany_returnsZeroStats() {
        stubCommonMocks("company-1");

        ManagerStatsResponse result = service.getManagerStats("company-1");

        assertThat(result).isNotNull();
        assertThat(result.getTotalApplications()).isEqualTo(0);
        assertThat(result.getTeamPerformance()).isEmpty();
        assertThat(result.getTopJobs()).isEmpty();
        assertThat(result.getStuckApplicationsCount()).isEqualTo(0);
        assertThat(result.getOfferAcceptedCount()).isEqualTo(0);
    }

    @Test
    void getManagerStats_withApplications_calculatesTeamPerformance() {
        String companyId = "company-2";

        Application reviewingApp = Application.builder()
                .id("app-1")
                .assignedTo("hr1")
                .jobId("job-1")
                .status(ApplicationStatus.REVIEWING)
                .statusHistory(new ArrayList<>(List.of(
                        Application.StatusChange.builder()
                                .previousStatus(ApplicationStatus.APPLIED)
                                .newStatus(ApplicationStatus.REVIEWING)
                                .changedAt(Instant.now())
                                .build())))
                .createdAt(Instant.now().minusSeconds(86400))
                .build();

        Application hiredApp = Application.builder()
                .id("app-2")
                .assignedTo("hr1")
                .jobId("job-1")
                .status(ApplicationStatus.HIRED)
                .statusHistory(new ArrayList<>())
                .createdAt(Instant.now().minusSeconds(86400))
                .build();

        when(applicationRepository.countByCompanyIdAndDeletedAtIsNull(companyId)).thenReturn(2L);
        when(mongoTemplate.aggregate(any(Aggregation.class), anyString(), any())).thenReturn(emptyAgg());
        when(applicationRepository.findByCompanyIdAndDeletedAtIsNull(companyId))
                .thenReturn(List.of(reviewingApp, hiredApp));
        when(applicationRepository.countByCompanyIdAndStatusInAndDeletedAtIsNullAndUpdatedAtBefore(
                anyString(), anyList(), any(Instant.class))).thenReturn(1L);
        when(applicationRepository.countByCompanyIdAndStatusAndDeletedAtIsNull(anyString(), any()))
                .thenReturn(1L);

        ManagerStatsResponse result = service.getManagerStats(companyId);

        assertThat(result.getTotalApplications()).isEqualTo(2);
        assertThat(result.getTeamPerformance()).hasSize(1);
        assertThat(result.getTeamPerformance().get(0).getHrUsername()).isEqualTo("hr1");
        assertThat(result.getTeamPerformance().get(0).getAssigned()).isEqualTo(2);
        assertThat(result.getTopJobs()).hasSize(1);
    }

    @Test
    void getManagerStats_applicationWithMissingTimestamps_skipsAvgTime() {
        String companyId = "company-3";

        Application app = Application.builder()
                .id("app-1")
                .assignedTo("hr1")
                .jobId("job-1")
                .status(ApplicationStatus.REVIEWING)
                .statusHistory(new ArrayList<>(List.of(
                        Application.StatusChange.builder()
                                .previousStatus(ApplicationStatus.APPLIED)
                                .newStatus(ApplicationStatus.REVIEWING)
                                .changedAt(null) // missing timestamp
                                .build())))
                .createdAt(null) // missing createdAt
                .build();

        when(applicationRepository.countByCompanyIdAndDeletedAtIsNull(companyId)).thenReturn(1L);
        when(mongoTemplate.aggregate(any(Aggregation.class), anyString(), any())).thenReturn(emptyAgg());
        when(applicationRepository.findByCompanyIdAndDeletedAtIsNull(companyId)).thenReturn(List.of(app));
        when(applicationRepository.countByCompanyIdAndStatusInAndDeletedAtIsNullAndUpdatedAtBefore(
                anyString(), anyList(), any(Instant.class))).thenReturn(0L);
        when(applicationRepository.countByCompanyIdAndStatusAndDeletedAtIsNull(anyString(), any()))
                .thenReturn(0L);

        ManagerStatsResponse result = service.getManagerStats(companyId);

        assertThat(result.getTeamPerformance().get(0).getAvgTimeToReviewDays()).isEqualTo(0.0);
    }

    @Test
    void getManagerStats_volumeTrend_emptyCompany_returnsDenseZeroSeries() {
        stubCommonMocks("company-4");

        ManagerStatsResponse result = service.getManagerStats("company-4");

        // Window is "since" (30 days ago) through "now" inclusive -> ~31 calendar days.
        assertThat(result.getVolumeTrend()).hasSizeGreaterThanOrEqualTo(30);
        assertThat(result.getVolumeTrend()).allSatisfy(d -> assertThat(d.getCount()).isZero());
    }

    @Test
    void getManagerStats_volumeTrend_allApplicationsOnOneDay_zeroFillsOtherDays() {
        String companyId = "company-5";
        when(applicationRepository.countByCompanyIdAndDeletedAtIsNull(companyId)).thenReturn(0L);
        when(applicationRepository.findByCompanyIdAndDeletedAtIsNull(companyId)).thenReturn(Collections.emptyList());
        when(applicationRepository.countByCompanyIdAndStatusInAndDeletedAtIsNullAndUpdatedAtBefore(
                anyString(), anyList(), any(Instant.class))).thenReturn(0L);
        when(applicationRepository.countByCompanyIdAndStatusAndDeletedAtIsNull(anyString(), any()))
                .thenReturn(0L);

        // Reproduces the reported bug: every application was created on the same
        // calendar day, so the raw $group aggregation only ever produces one bucket
        // and days with zero applications were silently dropped from the trend.
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        AggregationResults volumeTrendAgg = new AggregationResults<>(
                List.of(new Document("_id", today).append("count", 42)), new Document());

        // Call order in getManagerStats(): statusBreakdown, volumeTrend, offerRejected
        // -> volumeTrend is the 2nd aggregate(..., String, ...) call.
        when(mongoTemplate.aggregate(any(Aggregation.class), anyString(), any()))
                .thenReturn(emptyAgg(), volumeTrendAgg, emptyAgg());

        ManagerStatsResponse result = service.getManagerStats(companyId);

        assertThat(result.getVolumeTrend()).hasSizeGreaterThanOrEqualTo(30);
        assertThat(result.getVolumeTrend()).extracting(ManagerStatsResponse.DailyApplicationCount::getDate)
                .contains(today);

        ManagerStatsResponse.DailyApplicationCount todayEntry = result.getVolumeTrend().stream()
                .filter(d -> d.getDate().equals(today))
                .findFirst()
                .orElseThrow();
        assertThat(todayEntry.getCount()).isEqualTo(42);

        long otherDaysWithCount = result.getVolumeTrend().stream()
                .filter(d -> !d.getDate().equals(today))
                .filter(d -> d.getCount() != 0)
                .count();
        assertThat(otherDaysWithCount).isZero();
    }
}
