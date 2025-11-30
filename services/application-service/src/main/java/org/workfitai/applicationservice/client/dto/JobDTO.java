package org.workfitai.applicationservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO representing job data from job-service.
 * 
 * This is a simplified view of the Job entity containing
 * only the fields needed for application-service validation.
 * 
 * Fields used for validation:
 * - jobId: Reference for storing in Application
 * - status: Must be PUBLISHED to allow applications
 * - title: For display in application history
 * - companyName: For display purposes
 * 
 * Note: This is a subset of job-service's Job entity.
 * Add more fields as needed for business logic.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDTO {

    /**
     * Unique job identifier (UUID format).
     */
    private String jobId;

    /**
     * Job title (e.g., "Senior Java Developer").
     */
    private String title;

    /**
     * Job status - only PUBLISHED jobs accept applications.
     */
    private JobStatus status;

    /**
     * Company information (nested object from job-service).
     */
    private CompanyDTO company;

    /**
     * Job location.
     */
    private String location;

    /**
     * Minimum salary.
     */
    private BigDecimal salaryMin;

    /**
     * Maximum salary.
     */
    private BigDecimal salaryMax;

    /**
     * Salary currency (e.g., USD, VND).
     */
    private String currency;

    /**
     * Job expiration date.
     */
    private Instant expiresAt;

    /**
     * Helper method to check if job is accepting applications.
     * 
     * @return true if job is PUBLISHED
     */
    public boolean isAcceptingApplications() {
        return status == JobStatus.PUBLISHED;
    }

    /**
     * Helper method to get company name safely.
     * 
     * @return Company name or null
     */
    public String getCompanyName() {
        return company != null ? company.getName() : null;
    }
}
