package org.workfitai.applicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import org.springframework.data.mongodb.core.query.Query;
import org.workfitai.applicationservice.dto.response.JobStatsResponse;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class JobStatsServiceTest {

    @Mock MongoTemplate mongoTemplate;

    @InjectMocks JobStatsService service;

    private AggregationResults emptyAgg() {
        return new AggregationResults<>(Collections.emptyList(), new Document());
    }

    @Test
    void getJobStats_noApplications_returnsZeroStats() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(0L);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Application.class), any()))
                .thenReturn(emptyAgg());
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(Collections.emptyList());

        JobStatsResponse result = service.getJobStats("job-1");

        assertThat(result).isNotNull();
        assertThat(result.getTotalApplications()).isEqualTo(0);
        assertThat(result.getByStatus()).isNotNull();
        assertThat(result.getConversionRate().getAppliedToInterview()).isEqualTo(0.0);
        assertThat(result.getAverageTimeToReviewDays()).isEqualTo(0.0);
    }

    @Test
    void getJobStats_withApplicationsHavingStatusHistory_computesAvgReviewTime() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(2L);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Application.class), any()))
                .thenReturn(emptyAgg());

        Instant created = Instant.parse("2024-01-01T00:00:00Z");
        Instant reviewed = Instant.parse("2024-01-03T00:00:00Z");

        Application.StatusChange change = Application.StatusChange.builder()
                .previousStatus(ApplicationStatus.APPLIED)
                .newStatus(ApplicationStatus.REVIEWING)
                .changedAt(reviewed)
                .build();

        Application app = Application.builder()
                .id("app-1")
                .status(ApplicationStatus.REVIEWING)
                .createdAt(created)
                .statusHistory(new ArrayList<>(List.of(change)))
                .build();

        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(List.of(app));

        JobStatsResponse result = service.getJobStats("job-1");

        assertThat(result.getTotalApplications()).isEqualTo(2);
        assertThat(result.getAverageTimeToReviewDays()).isGreaterThan(0.0);
    }

    @Test
    void getJobStats_applicationWithNoStatusHistory_skipAverageReview() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(1L);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Application.class), any()))
                .thenReturn(emptyAgg());

        Application app = Application.builder()
                .id("app-1")
                .status(ApplicationStatus.APPLIED)
                .createdAt(Instant.now())
                .statusHistory(new ArrayList<>())
                .build();

        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(List.of(app));

        JobStatsResponse result = service.getJobStats("job-1");

        assertThat(result.getAverageTimeToReviewDays()).isEqualTo(0.0);
    }
}
