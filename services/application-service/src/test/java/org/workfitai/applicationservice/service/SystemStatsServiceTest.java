package org.workfitai.applicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;
import org.workfitai.applicationservice.dto.response.SystemStatsResponse;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class SystemStatsServiceTest {

    @Mock ApplicationRepository applicationRepository;
    @Mock MongoTemplate mongoTemplate;

    @InjectMocks SystemStatsService service;

    private AggregationResults emptyAgg() {
        return new AggregationResults<>(Collections.emptyList(), new Document());
    }

    private void stubAllMocks() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(0L);
        when(mongoTemplate.findDistinct(any(Query.class), anyString(), eq(Application.class), any()))
                .thenReturn(Collections.emptyList());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Application.class), any()))
                .thenReturn(emptyAgg());
        when(mongoTemplate.aggregate(any(Aggregation.class), anyString(), any()))
                .thenReturn(emptyAgg());
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(Collections.emptyList());
        when(applicationRepository.countByStatusAndDeletedAtIsNull(ApplicationStatus.HIRED)).thenReturn(0L);
    }

    @Test
    void getSystemStats_emptyDatabase_returnsZeroStats() {
        stubAllMocks();

        SystemStatsResponse result = service.getSystemStats();

        assertThat(result).isNotNull();
        assertThat(result.platformTotals()).isNotNull();
        assertThat(result.platformTotals().totalApplications()).isEqualTo(0);
        assertThat(result.byStatus()).isNotNull();
        assertThat(result.byCompany()).isNotNull();
        assertThat(result.topJobs()).isNotNull();
        assertThat(result.volumeTrend()).isNotNull();
        assertThat(result.offerAcceptedCount()).isEqualTo(0);
    }

    @Test
    void getSystemStats_withTotalCount_reflectsInPlatformTotals() {
        when(mongoTemplate.count(any(Query.class), eq(Application.class)))
                .thenReturn(100L)   // totalApplications
                .thenReturn(5L);    // totalDeleted
        when(mongoTemplate.findDistinct(any(Query.class), anyString(), eq(Application.class), any()))
                .thenReturn(List.of("c1", "c2", "c3"))
                .thenReturn(List.of("u1", "u2"));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq(Application.class), any()))
                .thenReturn(emptyAgg());
        when(mongoTemplate.aggregate(any(Aggregation.class), anyString(), any()))
                .thenReturn(emptyAgg());
        when(mongoTemplate.find(any(Query.class), eq(Application.class))).thenReturn(Collections.emptyList());
        when(applicationRepository.countByStatusAndDeletedAtIsNull(ApplicationStatus.HIRED)).thenReturn(10L);

        SystemStatsResponse result = service.getSystemStats();

        assertThat(result.platformTotals().totalApplications()).isEqualTo(100L);
        assertThat(result.offerAcceptedCount()).isEqualTo(10L);
    }

    @Test
    void getSystemStats_conversionRatesAlwaysPresent() {
        stubAllMocks();

        SystemStatsResponse result = service.getSystemStats();

        assertThat(result.platformConversionRates()).isNotNull();
        assertThat(result.platformConversionRates()).containsKey("APPLIED_TO_REVIEWING");
        assertThat(result.platformConversionRates()).containsKey("OFFER_TO_HIRED");
    }
}
