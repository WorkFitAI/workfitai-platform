package org.workfitai.applicationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.dto.response.SystemStatsResponse;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for system-wide admin statistics
 * Provides platform analytics across all companies
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemStatsService {

    private final ApplicationRepository applicationRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Get comprehensive system statistics
     * Cached for 30 minutes to reduce DB load
     * Uses MongoDB aggregation to avoid loading all documents (prevents OOM)
     */
    @Cacheable(value = "systemStats", unless = "#result == null")
    public SystemStatsResponse getSystemStats() {
        log.info("Calculating system-wide statistics using MongoDB aggregation...");

        // Calculate platform totals using aggregation
        SystemStatsResponse.PlatformTotals platformTotals = calculatePlatformTotalsWithAggregation();

        // Calculate stats by company (top 20)
        List<SystemStatsResponse.CompanyStats> byCompany = calculateCompanyStatsWithAggregation();

        // Calculate stats by status
        Map<String, Long> byStatus = calculateStatusStatsWithAggregation();

        // Calculate growth metrics using aggregation
        SystemStatsResponse.GrowthMetrics growthMetrics = calculateGrowthMetricsWithAggregation();

        // Calculate top jobs (top 10)
        List<SystemStatsResponse.TopJob> topJobs = calculateTopJobsWithAggregation();

        // Calculate average time to hire using aggregation
        String avgTimeToHire = calculateAvgTimeToHireWithAggregation();

        return new SystemStatsResponse(
            platformTotals,
            byCompany,
            byStatus,
            growthMetrics,
            topJobs,
            avgTimeToHire
        );
    }

    /**
     * Calculate platform-wide totals using MongoDB aggregation
     * Prevents OOM by avoiding loading all documents into memory
     */
    private SystemStatsResponse.PlatformTotals calculatePlatformTotalsWithAggregation() {
        // Count active applications (deletedAt is null)
        long totalApplications = mongoTemplate.count(
            org.springframework.data.mongodb.core.query.Query.query(
                Criteria.where("deletedAt").isNull()
            ),
            Application.class
        );

        // Count deleted applications
        long totalDeleted = mongoTemplate.count(
            org.springframework.data.mongodb.core.query.Query.query(
                Criteria.where("deletedAt").ne(null)
            ),
            Application.class
        );

        // Count drafts
        long totalDrafts = mongoTemplate.count(
            org.springframework.data.mongodb.core.query.Query.query(
                Criteria.where("isDraft").is(true)
            ),
            Application.class
        );

        // Count distinct companies
        long totalCompanies = mongoTemplate.findDistinct(
            org.springframework.data.mongodb.core.query.Query.query(
                Criteria.where("companyId").ne(null)
            ),
            "companyId",
            Application.class,
            String.class
        ).size();

        // Count distinct jobs
        long totalJobs = mongoTemplate.findDistinct(
            org.springframework.data.mongodb.core.query.Query.query(
                Criteria.where("jobId").exists(true)
            ),
            "jobId",
            Application.class,
            String.class
        ).size();

        return new SystemStatsResponse.PlatformTotals(
            totalApplications,
            totalDeleted,
            totalDrafts,
            totalCompanies,
            totalJobs
        );
    }

    /**
     * Calculate statistics by company using MongoDB aggregation
     * Top 20 companies by application count
     */
    private List<SystemStatsResponse.CompanyStats> calculateCompanyStatsWithAggregation() {
        // Aggregation pipeline:
        // 1. Match: active applications only (deletedAt is null)
        // 2. Match: non-null companyId
        // 3. Group: by companyId, count applications, count distinct jobs
        // 4. Sort: by application count descending
        // 5. Limit: top 20

        MatchOperation matchActive = Aggregation.match(Criteria.where("deletedAt").isNull());
        MatchOperation matchWithCompany = Aggregation.match(Criteria.where("companyId").ne(null));

        GroupOperation groupByCompany = Aggregation.group("companyId")
            .count().as("applications")
            .addToSet("jobId").as("jobIds");

        SortOperation sortByCount = Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "applications");
        LimitOperation limitTo20 = Aggregation.limit(20);

        Aggregation aggregation = Aggregation.newAggregation(
            matchActive,
            matchWithCompany,
            groupByCompany,
            sortByCount,
            limitTo20
        );

        var results = mongoTemplate.aggregate(aggregation, "applications", Map.class).getMappedResults();

        return results.stream()
            .map(result -> {
                String companyId = (String) result.get("_id");
                long applications = ((Number) result.get("applications")).longValue();
                @SuppressWarnings("unchecked")
                List<String> jobIds = (List<String>) result.get("jobIds");
                long activeJobs = jobIds != null ? jobIds.size() : 0;

                // TODO: Fetch actual time-to-hire for company (needs additional aggregation)
                double avgTimeToHire = 0.0;

                return new SystemStatsResponse.CompanyStats(
                    companyId,
                    "Company " + companyId, // TODO: Fetch company name from company-service
                    applications,
                    activeJobs,
                    avgTimeToHire
                );
            })
            .toList();
    }

    /**
     * Calculate statistics by status using MongoDB aggregation
     */
    private Map<String, Long> calculateStatusStatsWithAggregation() {
        MatchOperation matchActive = Aggregation.match(Criteria.where("deletedAt").isNull());
        GroupOperation groupByStatus = Aggregation.group("status").count().as("count");

        Aggregation aggregation = Aggregation.newAggregation(
            matchActive,
            groupByStatus
        );

        var results = mongoTemplate.aggregate(aggregation, "applications", Map.class).getMappedResults();

        return results.stream()
            .collect(Collectors.toMap(
                result -> (String) result.get("_id"),
                result -> ((Number) result.get("count")).longValue()
            ));
    }

    /**
     * Calculate growth metrics using MongoDB aggregation
     */
    private SystemStatsResponse.GrowthMetrics calculateGrowthMetricsWithAggregation() {
        Instant now = Instant.now();
        Instant sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);
        Instant thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);
        Instant sixtyDaysAgo = now.minus(60, ChronoUnit.DAYS);
        Instant oneYearAgo = now.minus(365, ChronoUnit.DAYS);
        Instant twoYearsAgo = now.minus(730, ChronoUnit.DAYS);

        long last7Days = mongoTemplate.count(
            org.springframework.data.mongodb.core.query.Query.query(
                Criteria.where("createdAt").gte(sevenDaysAgo)
            ),
            Application.class
        );

        long last30Days = mongoTemplate.count(
            org.springframework.data.mongodb.core.query.Query.query(
                Criteria.where("createdAt").gte(thirtyDaysAgo)
            ),
            Application.class
        );

        long previous30Days = mongoTemplate.count(
            org.springframework.data.mongodb.core.query.Query.query(
                Criteria.where("createdAt").gte(sixtyDaysAgo).lt(thirtyDaysAgo)
            ),
            Application.class
        );

        long lastYear = mongoTemplate.count(
            org.springframework.data.mongodb.core.query.Query.query(
                Criteria.where("createdAt").gte(oneYearAgo)
            ),
            Application.class
        );

        long previousYear = mongoTemplate.count(
            org.springframework.data.mongodb.core.query.Query.query(
                Criteria.where("createdAt").gte(twoYearsAgo).lt(oneYearAgo)
            ),
            Application.class
        );

        double monthOverMonth = previous30Days > 0
            ? ((double) last30Days - previous30Days) / previous30Days
            : 0.0;

        double yearOverYear = previousYear > 0
            ? ((double) lastYear - previousYear) / previousYear
            : 0.0;

        return new SystemStatsResponse.GrowthMetrics(
            last7Days,
            last30Days,
            monthOverMonth,
            yearOverYear
        );
    }

    /**
     * Calculate top jobs by application count using MongoDB aggregation
     */
    private List<SystemStatsResponse.TopJob> calculateTopJobsWithAggregation() {
        MatchOperation matchActive = Aggregation.match(Criteria.where("deletedAt").isNull());

        GroupOperation groupByJob = Aggregation.group("jobId")
            .count().as("applications")
            .sum(ConditionalOperators.when(Criteria.where("status").is(ApplicationStatus.HIRED))
                .then(1)
                .otherwise(0)).as("hires")
            .first("companyId").as("companyId");

        SortOperation sortByCount = Aggregation.sort(org.springframework.data.domain.Sort.Direction.DESC, "applications");
        LimitOperation limitTo10 = Aggregation.limit(10);

        Aggregation aggregation = Aggregation.newAggregation(
            matchActive,
            groupByJob,
            sortByCount,
            limitTo10
        );

        var results = mongoTemplate.aggregate(aggregation, "applications", Map.class).getMappedResults();

        return results.stream()
            .map(result -> {
                String jobId = (String) result.get("_id");
                long applications = ((Number) result.get("applications")).longValue();
                long hires = ((Number) result.get("hires")).longValue();
                String companyId = result.get("companyId") != null ? (String) result.get("companyId") : "Unknown";

                return new SystemStatsResponse.TopJob(
                    jobId,
                    "Job " + jobId, // TODO: Fetch job title from job-service
                    "Company " + companyId,
                    applications,
                    hires
                );
            })
            .toList();
    }

    /**
     * Calculate average time to hire (platform-wide) using MongoDB aggregation
     */
    private String calculateAvgTimeToHireWithAggregation() {
        // Match only HIRED applications with both submittedAt and updatedAt
        MatchOperation matchHired = Aggregation.match(
            Criteria.where("status").is(ApplicationStatus.HIRED)
                .and("submittedAt").ne(null)
                .and("updatedAt").ne(null)
        );

        // Project: Calculate duration in milliseconds
        ProjectionOperation projectDuration = Aggregation.project()
            .and("updatedAt").as("updatedAt")
            .and("submittedAt").as("submittedAt")
            .andExpression("updatedAt - submittedAt").as("durationMs");

        // Group: Calculate average duration
        GroupOperation groupAvg = Aggregation.group()
            .avg("durationMs").as("avgDurationMs");

        Aggregation aggregation = Aggregation.newAggregation(
            matchHired,
            projectDuration,
            groupAvg
        );

        var results = mongoTemplate.aggregate(aggregation, "applications", Map.class).getMappedResults();

        if (results.isEmpty()) {
            return "0.0 days";
        }

        Double avgDurationMs = (Double) results.get(0).get("avgDurationMs");
        if (avgDurationMs == null || avgDurationMs == 0) {
            return "0.0 days";
        }

        // Convert milliseconds to days
        double avgDays = avgDurationMs / (1000.0 * 60 * 60 * 24);
        return String.format("%.1f days", avgDays);
    }
}
