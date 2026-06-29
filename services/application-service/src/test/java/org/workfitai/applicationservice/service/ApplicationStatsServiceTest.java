package org.workfitai.applicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.DashboardStatsResponse;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ApplicationStatsServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock MongoTemplate mongoTemplate;
    @Mock ApplicationMapper applicationMapper;

    @InjectMocks ApplicationStatsService service;

    private AggregationResults emptyAgg() {
        return new AggregationResults<>(Collections.emptyList(), new Document());
    }

    @Test
    void getDashboardStats_emptyDatabase_returnsZeroStats() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(0L);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Application.class), any()))
                .thenReturn(emptyAgg());
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(Collections.emptyList());

        DashboardStatsResponse result = service.getDashboardStats("hr1");

        assertThat(result).isNotNull();
        assertThat(result.getTotalApplications()).isEqualTo(0);
        assertThat(result.getByStatus()).isNotEmpty(); // all statuses initialised to 0
        assertThat(result.getRecentApplications()).isEmpty();
        assertThat(result.getByJob()).isEmpty();
        assertThat(result.getWeeklyTrend()).isEmpty();
    }

    @Test
    void getDashboardStats_withRecentApplications_mapsToResponses() {
        Application app = Application.builder()
                .id("app-1")
                .status(ApplicationStatus.APPLIED)
                .createdAt(Instant.now())
                .build();
        ApplicationResponse response = new ApplicationResponse();

        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(1L);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Application.class), any()))
                .thenReturn(emptyAgg());
        // First find → recentApplications, second find → weeklyTrend
        when(mongoTemplate.find(any(Query.class), eq(Application.class)))
                .thenReturn(List.of(app))
                .thenReturn(Collections.emptyList());
        when(applicationMapper.toResponse(app)).thenReturn(response);

        DashboardStatsResponse result = service.getDashboardStats("hr1");

        assertThat(result.getTotalApplications()).isEqualTo(1);
        assertThat(result.getRecentApplications()).hasSize(1);
    }

    @Test
    void getDashboardStats_weeklyTrendGroupsByWeek() {
        Application app = Application.builder()
                .id("app-1")
                .status(ApplicationStatus.APPLIED)
                .createdAt(Instant.parse("2024-03-15T10:00:00Z"))
                .build();

        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(1L);
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Application.class), any()))
                .thenReturn(emptyAgg());
        when(mongoTemplate.find(any(Query.class), eq(Application.class)))
                .thenReturn(Collections.emptyList()) // recentApplications
                .thenReturn(List.of(app));            // weeklyTrend

        DashboardStatsResponse result = service.getDashboardStats("hr1");

        assertThat(result.getWeeklyTrend()).hasSize(1);
        assertThat(result.getWeeklyTrend().get(0).getCount()).isEqualTo(1);
    }
}
