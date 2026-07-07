package org.workfitai.applicationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.LimitOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.dto.response.SystemStatsResponse;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;
import org.workfitai.applicationservice.util.ApplicationFunnelCalculator;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for system-wide admin statistics
 * Provides platform analytics across all companies
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemStatsService {

    private static final int TOP_COMPANIES_LIMIT = 20;
    private static final int TOP_JOBS_LIMIT = 10;
    private static final int DAYS_PER_YEAR = 365;
    private static final int DAYS_TWO_YEARS = 730;
    private static final int VOLUME_TREND_DAYS = 30;
    private static final double MS_PER_DAY = 1000.0 * 60 * 60 * 24;

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

        // Calculate stats by status — reused for conversion rates
        Map<String, Long> byStatus = calculateStatusStatsWithAggregation();

        // Calculate growth metrics using aggregation
        SystemStatsResponse.GrowthMetrics growthMetrics = calculateGrowthMetricsWithAggregation();

        // Calculate top jobs (top 10)
        List<SystemStatsResponse.TopJob> topJobs = calculateTopJobsWithAggregation();

        // Calculate average time to hire using aggregation
        double avgTimeToHire = calculateAvgTimeToHireWithAggregation();

        // Platform-wide funnel conversion rates derived from byStatus
        Map<String, Double> platformConversionRates = ApplicationFunnelCalculator.calculateFunnelRates(byStatus);

        // Platform-wide daily volume trend (last 30 days) for line chart
        List<SystemStatsResponse.DailyCount> volumeTrend = calculateVolumeTrendWithAggregation();

        long offerAcceptedCount = applicationRepository.countByStatusAndDeletedAtIsNull(ApplicationStatus.HIRED);
        long offerRejectedCount = calculateOfferRejectedCount();

        return new SystemStatsResponse(
            platformTotals,
            byCompany,
            byStatus,
            growthMetrics,
            topJobs,
            avgTimeToHire,
            platformConversionRates,
            volumeTrend,
            offerAcceptedCount,
            offerRejectedCount
        );
    }

    /**
     * Calculate platform-wide totals using MongoDB aggregation
     * Prevents OOM by avoiding loading all documents into memory
     */
    private SystemStatsResponse.PlatformTotals calculatePlatformTotalsWithAggregation() {
        // Count active applications (deletedAt is null)
        long totalApplications = mongoTemplate.count(
            Query.query(
                Criteria.where("deletedAt").isNull()
            ),
            Application.class
        );

        // Count deleted applications
        long totalDeleted = mongoTemplate.count(
            Query.query(
                Criteria.where("deletedAt").ne(null)
            ),
            Application.class
        );

        // Count distinct companies
        long totalCompanies = mongoTemplate.findDistinct(
            Query.query(
                Criteria.where("companyId").ne(null)
            ),
            "companyId",
            Application.class,
            String.class
        ).size();

        // Count distinct jobs
        long totalJobs = mongoTemplate.findDistinct(
            Query.query(
                Criteria.where("jobId").exists(true)
            ),
            "jobId",
            Application.class,
            String.class
        ).size();

        return new SystemStatsResponse.PlatformTotals(
            totalApplications,
            totalDeleted,
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

        SortOperation sortByCount = Aggregation.sort(Sort.Direction.DESC, "applications");
        LimitOperation limitTo20 = Aggregation.limit(TOP_COMPANIES_LIMIT);

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
                    "Company " + companyId,
                    applications,
                    activeJobs,
                    avgTimeToHire
                );
            })
            .toList();
    }

    /**
     * Calculate statistics by status using MongoDB aggregation.
     * Counts each application once per unique status it has ever been in
     * (current status + all statuses from statusHistory), enabling accurate funnel rates.
     */
    private Map<String, Long> calculateStatusStatsWithAggregation() {
        MatchOperation matchActive = Aggregation.match(Criteria.where("deletedAt").isNull());

        // Build allStatuses = $setUnion of every status the application has ever had:
        // - statusHistory[].newStatus  (stages it transitioned TO)
        // - statusHistory[].previousStatus  (stages it transitioned FROM, captures initial APPLIED)
        // - [$status]  (current state, covers apps with no history yet)
        AggregationOperation addAllStatuses = ctx -> new Document("$addFields", new Document("allStatuses",
            new Document("$setUnion", List.of(
                new Document("$map", new Document()
                    .append("input", new Document("$ifNull", List.of("$statusHistory", List.of())))
                    .append("as", "h")
                    .append("in", "$$h.newStatus")),
                new Document("$map", new Document()
                    .append("input", new Document("$ifNull", List.of("$statusHistory", List.of())))
                    .append("as", "h")
                    .append("in", "$$h.previousStatus")),
                List.of("$status")
            ))
        ));

        Aggregation aggregation = Aggregation.newAggregation(
            matchActive,
            addAllStatuses,
            Aggregation.unwind("allStatuses"),
            Aggregation.match(Criteria.where("allStatuses").ne(null)),
            Aggregation.group("allStatuses").count().as("count")
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
        Instant oneYearAgo = now.minus(DAYS_PER_YEAR, ChronoUnit.DAYS);
        Instant twoYearsAgo = now.minus(DAYS_TWO_YEARS, ChronoUnit.DAYS);

        long last7Days = mongoTemplate.count(
            Query.query(
                Criteria.where("createdAt").gte(sevenDaysAgo)
            ),
            Application.class
        );

        long last30Days = mongoTemplate.count(
            Query.query(
                Criteria.where("createdAt").gte(thirtyDaysAgo)
            ),
            Application.class
        );

        long previous30Days = mongoTemplate.count(
            Query.query(
                Criteria.where("createdAt").gte(sixtyDaysAgo).lt(thirtyDaysAgo)
            ),
            Application.class
        );

        long lastYear = mongoTemplate.count(
            Query.query(
                Criteria.where("createdAt").gte(oneYearAgo)
            ),
            Application.class
        );

        long previousYear = mongoTemplate.count(
            Query.query(
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
            .first("companyId").as("companyId")
            .first("jobSnapshot.title").as("jobTitle");

        SortOperation sortByCount = Aggregation.sort(Sort.Direction.DESC, "applications");
        LimitOperation limitTo10 = Aggregation.limit(TOP_JOBS_LIMIT);

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
                String jobTitle = result.get("jobTitle") != null ? (String) result.get("jobTitle") : jobId;

                return new SystemStatsResponse.TopJob(
                    jobId,
                    jobTitle,
                    "Company " + companyId,
                    applications,
                    hires
                );
            })
            .toList();
    }

    /**
     * Calculate average time to hire (platform-wide) in days using MongoDB aggregation.
     * Returns a double (days) so the UI can use it directly in numeric comparisons and chart axes.
     */
    private double calculateAvgTimeToHireWithAggregation() {
        MatchOperation matchHired = Aggregation.match(
            Criteria.where("status").is(ApplicationStatus.HIRED)
                .and("createdAt").ne(null)
                .and("updatedAt").ne(null)
        );

        ProjectionOperation projectDuration = Aggregation.project()
            .and("updatedAt").as("updatedAt")
            .and("createdAt").as("createdAt")
            .andExpression("updatedAt - createdAt").as("durationMs");

        GroupOperation groupAvg = Aggregation.group()
            .avg("durationMs").as("avgDurationMs");

        Aggregation aggregation = Aggregation.newAggregation(matchHired, projectDuration, groupAvg);

        var results = mongoTemplate.aggregate(aggregation, "applications", Map.class).getMappedResults();

        if (results.isEmpty()) return 0.0;

        Double avgDurationMs = (Double) results.get(0).get("avgDurationMs");
        if (avgDurationMs == null || avgDurationMs == 0) return 0.0;

        return Math.round((avgDurationMs / MS_PER_DAY) * 10.0) / 10.0;
    }

    /**
     * Counts applications that had an OFFER→REJECTED status transition (platform-wide).
     * Uses $unwind + $match to find transitions in statusHistory, then deduplicates by _id.
     */
    private long calculateOfferRejectedCount() {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("deletedAt").isNull()),
            Aggregation.unwind("statusHistory"),
            Aggregation.match(Criteria.where("statusHistory.previousStatus").is("OFFER")
                    .and("statusHistory.newStatus").is("REJECTED")),
            Aggregation.group("_id")
        );
        return (long) mongoTemplate.aggregate(agg, "applications", Map.class).getMappedResults().size();
    }

    /**
     * Platform-wide daily application volume for the last 30 days.
     * Provides the time-series data needed for admin volume line charts.
     * Zero-fills days with no applications so callers always get one point per
     * calendar day in the window — MongoDB's $group only emits keys that exist,
     * so days without applications would otherwise be silently absent.
     */
    private List<SystemStatsResponse.DailyCount> calculateVolumeTrendWithAggregation() {
        Instant since = Instant.now().minus(VOLUME_TREND_DAYS, ChronoUnit.DAYS);

        MatchOperation match = Aggregation.match(
            Criteria.where("deletedAt").isNull().and("createdAt").gte(since)
        );

        ProjectionOperation project = Aggregation.project()
            .and(DateOperators.dateOf("createdAt").toString("%Y-%m-%d")).as("dateStr");

        GroupOperation group = Aggregation.group("dateStr").count().as("count");

        Aggregation aggregation = Aggregation.newAggregation(match, project, group);

        Map<String, Long> countsByDate = mongoTemplate.aggregate(aggregation, "applications", Map.class)
            .getMappedResults()
            .stream()
            .collect(Collectors.toMap(
                r -> (String) r.get("_id"),
                r -> ((Number) r.get("count")).longValue()
            ));

        // $dateToString defaults to UTC, so walk the same UTC calendar range to stay aligned.
        LocalDate startDay = LocalDate.ofInstant(since, ZoneOffset.UTC);
        LocalDate endDay = LocalDate.ofInstant(Instant.now(), ZoneOffset.UTC);

        List<SystemStatsResponse.DailyCount> denseTrend = new ArrayList<>();
        for (LocalDate day = startDay; !day.isAfter(endDay); day = day.plusDays(1)) {
            String dateStr = day.toString();
            denseTrend.add(new SystemStatsResponse.DailyCount(dateStr, countsByDate.getOrDefault(dateStr, 0L)));
        }
        return denseTrend;
    }
}
