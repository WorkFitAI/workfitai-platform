package org.workfitai.applicationservice.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.workfitai.applicationservice.dto.response.ManagerStatsResponse;
import org.workfitai.applicationservice.dto.response.ManagerStatsResponse.JobApplicationCount;
import org.workfitai.applicationservice.dto.response.ManagerStatsResponse.TeamPerformanceResponse;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;

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

    private final ApplicationRepository applicationRepository;

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

        return ManagerStatsResponse.builder()
                .totalApplications(totalApplications)
                .byStatus(byStatus)
                .teamPerformance(teamPerformance)
                .topJobs(topJobs)
                .byDepartment(new HashMap<>()) // TODO: Requires department field
                .build();
    }

    /**
     * Calculate status breakdown using count queries for better performance.
     *
     * @param companyId Company ID
     * @return Map of status to count
     */
    private Map<String, Long> calculateStatusBreakdown(String companyId) {
        Map<String, Long> byStatus = new HashMap<>();

        // Count for each status using repository methods
        for (ApplicationStatus status : ApplicationStatus.values()) {
            long count = applicationRepository.countByCompanyIdAndStatusAndDeletedAtIsNull(companyId, status);
            if (count > 0) {
                byStatus.put(status.name(), count);
            }
        }

        return byStatus;
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
     * Time between appliedAt (submittedAt or createdAt) and first status change.
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
                Instant appliedAt = app.getSubmittedAt() != null ? app.getSubmittedAt() : app.getCreatedAt();

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
                .limit(10)
                .toList();
    }
}
