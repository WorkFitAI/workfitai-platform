package org.workfitai.applicationservice.service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.dto.response.ManagerStatsResponse;
import org.workfitai.applicationservice.dto.response.ManagerStatsResponse.DailyApplicationCount;
import org.workfitai.applicationservice.dto.response.ManagerStatsResponse.JobApplicationCount;
import org.workfitai.applicationservice.dto.response.ManagerStatsResponse.TeamPerformanceResponse;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;
import org.workfitai.applicationservice.util.ApplicationFunnelCalculator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for HR Manager dashboard statistics.
 *
 * Provides company-wide metrics:
 * - Total applications and status breakdown
 * - Team performance (per HR user)
 * - Top jobs by applicant count
 *
 * Performance Notes:
 * - Uses in-memory aggregation (simple for Phase 3)
 * - Phase 4: Use MongoDB aggregation pipeline for better performance
 * - Phase 5: Add caching (10-min TTL)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManagerStatsService {

    private static final int TOP_JOBS_LIMIT = 10;
    private static final int STUCK_APPLICATION_DAYS = 7;
    private static final int VOLUME_TREND_DAYS = 30;

    private final ApplicationRepository applicationRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Get manager dashboard statistics for a company.
     *
     * Performance: Uses count queries to avoid loading all applications into memory.
     * Only loads applications for team performance calculation (limited scope).
     *
     * @param companyId Company ID
     * @return Manager statistics
     */
    public ManagerStatsResponse getManagerStats(String companyId) {
        log.info("Calculating manager stats for company: {}", companyId);

        // Total applications (count query - O(1))
        long totalApplications = applicationRepository.countByCompanyIdAndDeletedAtIsNull(companyId);

        // Applications by status (count queries - O(status_count))
        Map<String, Long> byStatus = calculateStatusBreakdown(companyId);

        // For team performance and top jobs, we still need to load applications
        // But this is acceptable for Phase 3 as it's limited by company scope
        List<Application> applications = applicationRepository
                .findByCompanyIdAndDeletedAtIsNull(companyId);

        // Team performance
        List<TeamPerformanceResponse> teamPerformance = calculateTeamPerformance(applications);

        // Top jobs by applicant count
        List<JobApplicationCount> topJobs = calculateTopJobs(applications);

        long stuckCount = calculateStuckApplications(companyId);
        Map<String, Double> conversionRates = calculateConversionRates(byStatus);
        List<DailyApplicationCount> volumeTrend = calculateVolumeTrend(companyId);

        long offerAcceptedCount = applicationRepository
                .countByCompanyIdAndStatusAndDeletedAtIsNull(companyId, ApplicationStatus.HIRED);
        long offerRejectedCount = calculateOfferRejectedCount(companyId);

        return ManagerStatsResponse.builder()
                .totalApplications(totalApplications)
                .byStatus(byStatus)
                .teamPerformance(teamPerformance)
                .topJobs(topJobs)
                .byDepartment(new HashMap<>()) // TODO: Requires department field
                .stuckApplicationsCount(stuckCount)
                .conversionRates(conversionRates)
                .volumeTrend(volumeTrend)
                .offerAcceptedCount(offerAcceptedCount)
                .offerRejectedCount(offerRejectedCount)
                .build();
    }

    /**
     * Counts applications for a company that had an OFFER→REJECTED status transition.
     * Deduplicates per application _id to avoid double-counting.
     */
    private long calculateOfferRejectedCount(String companyId) {
        Aggregation agg = Aggregation.newAggregation(
            Aggregation.match(Criteria.where("deletedAt").isNull().and("companyId").is(companyId)),
            Aggregation.unwind("statusHistory"),
            Aggregation.match(Criteria.where("statusHistory.previousStatus").is("OFFER")
                    .and("statusHistory.newStatus").is("REJECTED")),
            Aggregation.group("_id")
        );
        return (long) mongoTemplate.aggregate(agg, "applications", Map.class).getMappedResults().size();
    }

    private long calculateStuckApplications(String companyId) {
        Instant cutoff = Instant.now().minus(STUCK_APPLICATION_DAYS, ChronoUnit.DAYS);
        return applicationRepository.countByCompanyIdAndStatusInAndDeletedAtIsNullAndUpdatedAtBefore(
                companyId,
                List.of(ApplicationStatus.APPLIED, ApplicationStatus.REVIEWING),
                cutoff
        );
    }

    private Map<String, Double> calculateConversionRates(Map<String, Long> byStatus) {
        return ApplicationFunnelCalculator.calculateFunnelRates(byStatus);
    }

    private List<DailyApplicationCount> calculateVolumeTrend(String companyId) {
        Instant since = Instant.now().minus(VOLUME_TREND_DAYS, ChronoUnit.DAYS);

        MatchOperation match = Aggregation.match(
                Criteria.where("companyId").is(companyId)
                        .and("deletedAt").isNull()
                        .and("createdAt").gte(since)
        );

        ProjectionOperation project = Aggregation.project()
                .and(DateOperators.dateOf("createdAt").toString("%Y-%m-%d")).as("dateStr");

        GroupOperation group = Aggregation.group("dateStr").count().as("count");
        SortOperation sort = Aggregation.sort(Sort.Direction.ASC, "_id");

        Aggregation aggregation = Aggregation.newAggregation(match, project, group, sort);

        return mongoTemplate.aggregate(aggregation, "applications", Map.class)
                .getMappedResults()
                .stream()
                .map(r -> new DailyApplicationCount(
                        (String) r.get("_id"),
                        ((Number) r.get("count")).longValue()
                ))
                .toList();
    }

    /**
     * Calculate status breakdown counting each application once per unique status
     * it has ever been in (current status + full statusHistory), so funnel rates
     * reflect real pipeline throughput rather than just current-state snapshots.
     */
    private Map<String, Long> calculateStatusBreakdown(String companyId) {
        MatchOperation matchActive = Aggregation.match(
            Criteria.where("companyId").is(companyId).and("deletedAt").isNull()
        );

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
     * Calculate team performance metrics for each HR user.
     */
    private List<TeamPerformanceResponse> calculateTeamPerformance(List<Application> applications) {
        // Group by assignedTo
        Map<String, List<Application>> byHR = applications.stream()
                .filter(app -> app.getAssignedTo() != null)
                .collect(Collectors.groupingBy(Application::getAssignedTo));

        List<TeamPerformanceResponse> performance = new ArrayList<>();

        for (Map.Entry<String, List<Application>> entry : byHR.entrySet()) {
            String hrUsername = entry.getKey();
            List<Application> assignedApps = entry.getValue();

            long assigned = assignedApps.size();

            // Reviewed = apps that moved from APPLIED status
            long reviewed = assignedApps.stream()
                    .filter(app -> !app.getStatus().equals(ApplicationStatus.APPLIED))
                    .count();

            // Average time to review (days)
            double avgTimeToReviewDays = calculateAvgTimeToReview(assignedApps);

            // Conversion rate (HIRED / reviewed)
            // Note: HIRED status represents successful conversions (accepted offers)
            long hiredCount = assignedApps.stream()
                    .filter(app -> app.getStatus().equals(ApplicationStatus.HIRED))
                    .count();
            double conversionRate = reviewed > 0 ? (double) hiredCount / reviewed : 0.0;

            performance.add(TeamPerformanceResponse.builder()
                    .hrUsername(hrUsername)
                    .assigned(assigned)
                    .reviewed(reviewed)
                    .avgTimeToReviewDays(avgTimeToReviewDays)
                    .conversionRate(conversionRate)
                    .build());
        }

        return performance;
    }

    /**
     * Calculate average time to review in days.
     * Time between createdAt and first status change.
     */
    private double calculateAvgTimeToReview(List<Application> applications) {
        List<Long> reviewTimes = new ArrayList<>();

        for (Application app : applications) {
            // Skip if still in APPLIED status (not reviewed yet)
            if (app.getStatus().equals(ApplicationStatus.APPLIED)) {
                continue;
            }

            // Find first status change
            if (app.getStatusHistory() != null && !app.getStatusHistory().isEmpty()) {
                Instant firstChangeAt = app.getStatusHistory().get(0).getChangedAt();
                Instant appliedAt = app.getCreatedAt();

                long daysBetween = Duration.between(appliedAt, firstChangeAt).toDays();
                reviewTimes.add(daysBetween);
            }
        }

        if (reviewTimes.isEmpty()) {
            return 0.0;
        }

        return reviewTimes.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0);
    }

    /**
     * Calculate top jobs by applicant count (top 10).
     */
    private List<JobApplicationCount> calculateTopJobs(List<Application> applications) {
        // Group by jobId
        Map<String, List<Application>> byJob = applications.stream()
                .collect(Collectors.groupingBy(Application::getJobId));

        return byJob.entrySet().stream()
                .map(entry -> {
                    String jobId = entry.getKey();
                    List<Application> jobApps = entry.getValue();
                    long count = jobApps.size();

                    // Get job title from first application's snapshot
                    String jobTitle = jobApps.get(0).getJobSnapshot() != null
                            ? jobApps.get(0).getJobSnapshot().getTitle()
                            : "Unknown";

                    return JobApplicationCount.builder()
                            .jobId(jobId)
                            .jobTitle(jobTitle)
                            .applicantCount(count)
                            .build();
                })
                .sorted((a, b) -> Long.compare(b.getApplicantCount(), a.getApplicantCount()))
                .limit(TOP_JOBS_LIMIT)
                .toList();
    }
}
