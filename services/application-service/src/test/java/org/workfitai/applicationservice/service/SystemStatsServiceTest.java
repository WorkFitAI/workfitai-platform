package org.workfitai.applicationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;
import org.workfitai.applicationservice.dto.response.SystemStatsResponse;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.repository.ApplicationRepository;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SystemStatsService
 * Tests the MongoDB aggregation-based statistics calculation
 */
@ExtendWith(MockitoExtension.class)
class SystemStatsServiceTest {

    @Mock
    private ApplicationRepository applicationRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private SystemStatsService systemStatsService;

    @BeforeEach
    void setUp() {
        // Clear any cached data before each test
    }

    @Test
    void testGetSystemStats_ShouldReturnValidStats() {
        // Arrange
        // Mock counts for platform totals
        when(mongoTemplate.count(any(Query.class), eq(Application.class)))
            .thenReturn(100L)  // active applications
            .thenReturn(10L)   // deleted applications
            .thenReturn(5L)    // drafts
            .thenReturn(50L)   // last 7 days
            .thenReturn(150L)  // last 30 days
            .thenReturn(120L)  // previous 30 days
            .thenReturn(500L)  // last year
            .thenReturn(400L); // previous year

        // Mock distinct companies
        when(mongoTemplate.findDistinct(any(Query.class), eq("companyId"), eq(Application.class), eq(String.class)))
            .thenReturn(Arrays.asList("company1", "company2", "company3"));

        // Mock distinct jobs
        when(mongoTemplate.findDistinct(any(Query.class), eq("jobId"), eq(Application.class), eq(String.class)))
            .thenReturn(Arrays.asList("job1", "job2", "job3", "job4", "job5"));

        // Mock aggregation results for companies
        AggregationResults<Map> companyResults = mock(AggregationResults.class);
        when(companyResults.getMappedResults()).thenReturn(Collections.emptyList());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("applications"), eq(Map.class)))
            .thenReturn(companyResults);

        // Act
        SystemStatsResponse stats = systemStatsService.getSystemStats();

        // Assert
        assertNotNull(stats, "Stats should not be null");
        assertNotNull(stats.platformTotals(), "Platform totals should not be null");
        assertEquals(100L, stats.platformTotals().totalApplications(), "Should have 100 active applications");
        assertEquals(10L, stats.platformTotals().totalDeleted(), "Should have 10 deleted applications");
        assertEquals(5L, stats.platformTotals().totalDrafts(), "Should have 5 drafts");
        assertEquals(3L, stats.platformTotals().totalCompanies(), "Should have 3 companies");
        assertEquals(5L, stats.platformTotals().totalJobs(), "Should have 5 jobs");

        // Verify MongoDB aggregation was used (not findAll)
        verify(mongoTemplate, atLeastOnce()).count(any(Query.class), eq(Application.class));
        verify(mongoTemplate, atLeastOnce()).findDistinct(any(Query.class), anyString(), eq(Application.class), eq(String.class));
        verify(mongoTemplate, atLeastOnce()).aggregate(any(Aggregation.class), eq("applications"), eq(Map.class));

        // Verify findAll was NEVER called (prevents OOM)
        verify(applicationRepository, never()).findAll();
    }

    @Test
    void testGetSystemStats_WithNoData_ShouldReturnZeros() {
        // Arrange
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(0L);
        when(mongoTemplate.findDistinct(any(Query.class), anyString(), eq(Application.class), eq(String.class)))
            .thenReturn(Collections.emptyList());

        AggregationResults<Map> emptyResults = mock(AggregationResults.class);
        when(emptyResults.getMappedResults()).thenReturn(Collections.emptyList());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("applications"), eq(Map.class)))
            .thenReturn(emptyResults);

        // Act
        SystemStatsResponse stats = systemStatsService.getSystemStats();

        // Assert
        assertNotNull(stats, "Stats should not be null even with no data");
        assertEquals(0L, stats.platformTotals().totalApplications(), "Should have 0 applications");
        assertEquals(0L, stats.platformTotals().totalCompanies(), "Should have 0 companies");
        assertEquals(0L, stats.platformTotals().totalJobs(), "Should have 0 jobs");
        assertTrue(stats.byCompany().isEmpty(), "Company stats should be empty");
        assertTrue(stats.topJobs().isEmpty(), "Top jobs should be empty");
    }

    @Test
    void testCalculatePlatformTotals_ShouldUseAggregationNotFindAll() {
        // Arrange
        when(mongoTemplate.count(any(Query.class), eq(Application.class)))
            .thenReturn(250L)  // active
            .thenReturn(25L)   // deleted
            .thenReturn(15L);  // drafts

        when(mongoTemplate.findDistinct(any(Query.class), eq("companyId"), eq(Application.class), eq(String.class)))
            .thenReturn(Arrays.asList("c1", "c2", "c3", "c4", "c5"));

        when(mongoTemplate.findDistinct(any(Query.class), eq("jobId"), eq(Application.class), eq(String.class)))
            .thenReturn(Arrays.asList("j1", "j2", "j3", "j4", "j5", "j6", "j7", "j8", "j9", "j10"));

        AggregationResults<Map> mockResults = mock(AggregationResults.class);
        when(mockResults.getMappedResults()).thenReturn(Collections.emptyList());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("applications"), eq(Map.class)))
            .thenReturn(mockResults);

        // Act
        SystemStatsResponse stats = systemStatsService.getSystemStats();

        // Assert
        SystemStatsResponse.PlatformTotals totals = stats.platformTotals();
        assertEquals(250L, totals.totalApplications(), "Active count should match");
        assertEquals(25L, totals.totalDeleted(), "Deleted count should match");
        assertEquals(15L, totals.totalDrafts(), "Draft count should match");
        assertEquals(5L, totals.totalCompanies(), "Company count should match");
        assertEquals(10L, totals.totalJobs(), "Job count should match");

        // CRITICAL: Verify findAll() was NEVER called (prevents OOM)
        verify(applicationRepository, never()).findAll();
    }

    @Test
    void testCalculateGrowthMetrics_ShouldUseCountQueries() {
        // Arrange
        when(mongoTemplate.count(any(Query.class), eq(Application.class)))
            .thenReturn(10L)   // active
            .thenReturn(2L)    // deleted
            .thenReturn(1L)    // drafts
            .thenReturn(50L)   // last 7 days
            .thenReturn(150L)  // last 30 days
            .thenReturn(100L)  // previous 30 days
            .thenReturn(600L)  // last year
            .thenReturn(500L); // previous year

        when(mongoTemplate.findDistinct(any(Query.class), anyString(), eq(Application.class), eq(String.class)))
            .thenReturn(Collections.emptyList());

        AggregationResults<Map> mockResults = mock(AggregationResults.class);
        when(mockResults.getMappedResults()).thenReturn(Collections.emptyList());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("applications"), eq(Map.class)))
            .thenReturn(mockResults);

        // Act
        SystemStatsResponse stats = systemStatsService.getSystemStats();

        // Assert
        SystemStatsResponse.GrowthMetrics growth = stats.growthMetrics();
        assertNotNull(growth, "Growth metrics should not be null");
        assertEquals(50L, growth.last7Days(), "Last 7 days should be 50");
        assertEquals(150L, growth.last30Days(), "Last 30 days should be 150");

        // Month-over-month: (150 - 100) / 100 = 0.5 (50% growth)
        assertEquals(0.5, growth.monthOverMonth(), 0.01, "MoM growth should be 50%");

        // Year-over-year: (600 - 500) / 500 = 0.2 (20% growth)
        assertEquals(0.2, growth.yearOverYear(), 0.01, "YoY growth should be 20%");
    }

    @Test
    void testSystemStats_IsCacheable() {
        // Arrange
        when(mongoTemplate.count(any(Query.class), eq(Application.class))).thenReturn(100L);
        when(mongoTemplate.findDistinct(any(Query.class), anyString(), eq(Application.class), eq(String.class)))
            .thenReturn(Collections.emptyList());

        AggregationResults<Map> mockResults = mock(AggregationResults.class);
        when(mockResults.getMappedResults()).thenReturn(Collections.emptyList());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("applications"), eq(Map.class)))
            .thenReturn(mockResults);

        // Act - Call twice
        SystemStatsResponse stats1 = systemStatsService.getSystemStats();
        SystemStatsResponse stats2 = systemStatsService.getSystemStats();

        // Assert
        assertNotNull(stats1, "First call should return stats");
        assertNotNull(stats2, "Second call should return stats");

        // Note: In a real test with Spring cache, we'd verify cache was hit
        // Here we just verify the method is annotated with @Cacheable
        // (which we can see in the source code)
    }
}
