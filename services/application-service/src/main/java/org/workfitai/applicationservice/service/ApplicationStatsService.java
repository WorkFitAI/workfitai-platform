package org.workfitai.applicationservice.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.DashboardStatsResponse;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for calculating HR dashboard statistics.
 *
 * Provides aggregated metrics for:
 * - Total application counts
 * - Applications by status distribution
 * - Recent applications (last 10)
 * - Applications grouped by job
 * - Weekly application trends
 *
 * Uses MongoDB aggregation framework for efficient data processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ApplicationStatsService {

    private final ApplicationRepository applicationRepository;
    private final MongoTemplate mongoTemplate;
    private final ApplicationMapper applicationMapper;

    /**
     * Generate dashboard statistics for HR.
     *
     * @param hrUsername HR username (for filtering jobs they manage)
     * @return Dashboard statistics
     */
    public DashboardStatsResponse getDashboardStats(String hrUsername) {
        log.info("Generating dashboard statistics for HR: {}", hrUsername);

        // For now, get all applications (job-level filtering can be added later)
        Criteria criteria = Criteria.where("deletedAt").isNull();

        // TODO: Filter by jobs managed by this HR (requires integration with job-service)
        // For Phase 2, showing all applications

        // Total count
        long totalApplications = mongoTemplate.count(
            org.springframework.data.mongodb.core.query.Query.query(criteria),
            Application.class
        );

        // By status
        Map<String, Long> byStatus = getApplicationsByStatus(criteria);

        // Recent applications (last 10)
        List<ApplicationResponse> recentApplications = getRecentApplications(criteria, 10);

        // By job
        List<DashboardStatsResponse.JobApplicationCount> byJob = getApplicationsByJob(criteria);

        // Weekly trend
        List<DashboardStatsResponse.WeeklyCount> weeklyTrend = getWeeklyTrend(criteria);

        log.info("Dashboard stats generated: total={}, recentCount={}, jobsCount={}",
                 totalApplications, recentApplications.size(), byJob.size());

        return DashboardStatsResponse.builder()
                .totalApplications(totalApplications)
                .byStatus(byStatus)
                .recentApplications(recentApplications)
                .byJob(byJob)
                .weeklyTrend(weeklyTrend)
                .build();
    }

    /**
     * Get application count by status.
     *
     * @param baseCriteria Base filter criteria
     * @return Map of status to count
     */
    private Map<String, Long> getApplicationsByStatus(Criteria baseCriteria) {
        MatchOperation matchStage = Aggregation.match(baseCriteria);
        GroupOperation groupStage = Aggregation.group("status").count().as("count");

        Aggregation aggregation = Aggregation.newAggregation(matchStage, groupStage);

        AggregationResults<StatusCount> results = mongoTemplate.aggregate(
            aggregation,
            Application.class,
            StatusCount.class
        );

        Map<String, Long> statusMap = new LinkedHashMap<>();

        // Initialize all statuses with 0
        for (ApplicationStatus status : ApplicationStatus.values()) {
            statusMap.put(status.name(), 0L);
        }

        // Fill in actual counts
        results.getMappedResults().forEach(result -> {
            if (result.getId() != null) {
                statusMap.put(result.getId().name(), result.getCount());
            }
        });

        return statusMap;
    }

    /**
     * Get recent applications.
     *
     * @param baseCriteria Base filter criteria
     * @param limit Number of applications to return
     * @return List of recent applications
     */
    private List<ApplicationResponse> getRecentApplications(Criteria baseCriteria, int limit) {
        org.springframework.data.mongodb.core.query.Query query =
            org.springframework.data.mongodb.core.query.Query.query(baseCriteria)
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .limit(limit);

        List<Application> applications = mongoTemplate.find(query, Application.class);

        return applications.stream()
                .map(applicationMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get application counts grouped by job.
     *
     * @param baseCriteria Base filter criteria
     * @return List of job application counts
     */
    private List<DashboardStatsResponse.JobApplicationCount> getApplicationsByJob(Criteria baseCriteria) {
        MatchOperation matchStage = Aggregation.match(baseCriteria);
        GroupOperation groupStage = Aggregation.group("jobId")
                .count().as("count")
                .first("jobSnapshot.title").as("jobTitle");

        Aggregation aggregation = Aggregation.newAggregation(matchStage, groupStage);

        AggregationResults<JobCount> results = mongoTemplate.aggregate(
            aggregation,
            Application.class,
            JobCount.class
        );

        return results.getMappedResults().stream()
                .map(result -> DashboardStatsResponse.JobApplicationCount.builder()
                        .jobId(result.getId())
                        .jobTitle(result.getJobTitle() != null ? result.getJobTitle() : "Unknown Job")
                        .count(result.getCount())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get weekly application trend (last 8 weeks).
     *
     * @param baseCriteria Base filter criteria
     * @return List of weekly counts
     */
    private List<DashboardStatsResponse.WeeklyCount> getWeeklyTrend(Criteria baseCriteria) {
        // Get applications from last 8 weeks
        Instant eightWeeksAgo = Instant.now().minusSeconds(8 * 7 * 24 * 60 * 60);
        Criteria criteriaWithDate = baseCriteria.and("createdAt").gte(eightWeeksAgo);

        org.springframework.data.mongodb.core.query.Query query =
            org.springframework.data.mongodb.core.query.Query.query(criteriaWithDate);

        List<Application> applications = mongoTemplate.find(query, Application.class);

        // Group by week
        Map<String, Long> weeklyMap = applications.stream()
                .collect(Collectors.groupingBy(
                    app -> formatWeek(app.getCreatedAt()),
                    Collectors.counting()
                ));

        return weeklyMap.entrySet().stream()
                .map(entry -> DashboardStatsResponse.WeeklyCount.builder()
                        .week(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .sorted((a, b) -> a.getWeek().compareTo(b.getWeek()))
                .collect(Collectors.toList());
    }

    /**
     * Format instant as ISO week string (e.g., "2024-W01").
     *
     * @param instant The instant to format
     * @return Week string in format YYYY-Www
     */
    private String formatWeek(Instant instant) {
        var localDate = instant.atZone(ZoneId.systemDefault()).toLocalDate();
        WeekFields weekFields = WeekFields.of(Locale.getDefault());
        int weekOfYear = localDate.get(weekFields.weekOfYear());
        int year = localDate.getYear();
        return String.format("%d-W%02d", year, weekOfYear);
    }

    // Helper classes for aggregation results

    @lombok.Data
    private static class StatusCount {
        private ApplicationStatus id;
        private long count;
    }

    @lombok.Data
    private static class JobCount {
        private String id; // jobId
        private String jobTitle;
        private long count;
    }
}
