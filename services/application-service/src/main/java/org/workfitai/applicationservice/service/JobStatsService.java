package org.workfitai.applicationservice.service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.applicationservice.dto.response.JobStatsResponse;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for calculating job-specific application statistics.
 *
 * Provides metrics for:
 * - Total application count per job
 * - Status distribution (funnel view)
 * - Conversion rates through hiring stages
 * - Average time to review
 *
 * Conversion rates calculated:
 * - Applied → Interview: (INTERVIEW + OFFER + HIRED) / APPLIED
 * - Interview → Offer: (OFFER + HIRED) / INTERVIEW
 * - Offer → Hired: HIRED / OFFER
 *
 * Uses MongoDB aggregation for efficient calculation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class JobStatsService {

    private final MongoTemplate mongoTemplate;

    /**
     * Get comprehensive statistics for a specific job.
     *
     * @param jobId Job ID to analyze
     * @return Job statistics including conversion rates
     */
    public JobStatsResponse getJobStats(String jobId) {
        log.info("Calculating statistics for job: {}", jobId);

        Criteria criteria = Criteria.where("jobId").is(jobId)
                .and("deletedAt").isNull();

        // Total applications
        long totalApplications = mongoTemplate.count(
            org.springframework.data.mongodb.core.query.Query.query(criteria),
            Application.class
        );

        // Applications by status
        Map<String, Long> byStatus = getApplicationsByStatus(jobId);

        // Conversion rates
        JobStatsResponse.ConversionRates conversionRates = calculateConversionRates(byStatus);

        // Average time to review
        double averageTimeToReviewDays = calculateAverageTimeToReview(jobId);

        log.info("Job stats calculated: jobId={}, total={}, avgReviewTime={}",
                 jobId, totalApplications, averageTimeToReviewDays);

        return JobStatsResponse.builder()
                .totalApplications(totalApplications)
                .byStatus(byStatus)
                .conversionRate(conversionRates)
                .averageTimeToReviewDays(averageTimeToReviewDays)
                .build();
    }

    /**
     * Get application count by status for a job.
     *
     * @param jobId Job ID
     * @return Map of status to count
     */
    private Map<String, Long> getApplicationsByStatus(String jobId) {
        Criteria criteria = Criteria.where("jobId").is(jobId)
                .and("deletedAt").isNull();

        MatchOperation matchStage = Aggregation.match(criteria);
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
     * Calculate conversion rates through the hiring funnel.
     *
     * @param byStatus Status distribution map
     * @return Conversion rates
     */
    private JobStatsResponse.ConversionRates calculateConversionRates(Map<String, Long> byStatus) {
        long applied = byStatus.getOrDefault("APPLIED", 0L);
        long reviewing = byStatus.getOrDefault("REVIEWING", 0L);
        long interview = byStatus.getOrDefault("INTERVIEW", 0L);
        long offer = byStatus.getOrDefault("OFFER", 0L);
        long hired = byStatus.getOrDefault("HIRED", 0L);

        // Applied to Interview (includes all stages beyond applied)
        double appliedToInterview = 0.0;
        if (applied > 0) {
            appliedToInterview = (double) (interview + offer + hired) / applied;
        }

        // Interview to Offer
        double interviewToOffer = 0.0;
        if (interview > 0) {
            interviewToOffer = (double) (offer + hired) / interview;
        }

        // Offer to Hired
        double offerToHired = 0.0;
        if (offer > 0) {
            offerToHired = (double) hired / offer;
        }

        return JobStatsResponse.ConversionRates.builder()
                .appliedToInterview(appliedToInterview)
                .interviewToOffer(interviewToOffer)
                .offerToHired(offerToHired)
                .build();
    }

    /**
     * Calculate average time from application submission to first status change.
     *
     * @param jobId Job ID
     * @return Average time in days
     */
    private double calculateAverageTimeToReview(String jobId) {
        Criteria criteria = Criteria.where("jobId").is(jobId)
                .and("deletedAt").isNull()
                .and("statusHistory").exists(true).ne(null);

        org.springframework.data.mongodb.core.query.Query query =
            org.springframework.data.mongodb.core.query.Query.query(criteria);

        List<Application> applications = mongoTemplate.find(query, Application.class);

        if (applications.isEmpty()) {
            return 0.0;
        }

        double totalDays = 0.0;
        int count = 0;

        for (Application app : applications) {
            if (app.getStatusHistory() != null && !app.getStatusHistory().isEmpty()) {
                // Get first status change (excluding initial APPLIED status)
                var firstChange = app.getStatusHistory().stream()
                    .filter(sc -> sc.getPreviousStatus() == ApplicationStatus.APPLIED)
                    .findFirst();

                if (firstChange.isPresent() && app.getCreatedAt() != null) {
                    Instant createdAt = app.getCreatedAt();
                    Instant reviewedAt = firstChange.get().getChangedAt();

                    Duration duration = Duration.between(createdAt, reviewedAt);
                    double days = duration.toDays() + (duration.toHoursPart() / 24.0);
                    totalDays += days;
                    count++;
                }
            }
        }

        return count > 0 ? totalDays / count : 0.0;
    }

    // Helper class for aggregation results

    @lombok.Data
    private static class StatusCount {
        private ApplicationStatus id;
        private long count;
    }
}
