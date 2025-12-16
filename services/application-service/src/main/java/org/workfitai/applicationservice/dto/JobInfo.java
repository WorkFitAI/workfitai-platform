package org.workfitai.applicationservice.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Job information retrieved from job-service.
 * Used to create a snapshot in the application document.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobInfo {

    /**
     * Job ID.
     */
    private String id;

    /**
     * Job title.
     */
    private String title;

    /**
     * Company name posting the job.
     */
    private String companyName;

    /**
     * Job location.
     */
    private String location;

    /**
     * Employment type (FULL_TIME, PART_TIME, CONTRACT, etc.).
     */
    private String employmentType;

    /**
     * Required experience level (ENTRY, MID, SENIOR, etc.).
     */
    private String experienceLevel;

    /**
     * Job status (should be PUBLISHED for applications).
     */
    private String status;

    /**
     * Company ID for the job.
     * Used for company-level filtering and access control.
     */
    private String companyId;

    /**
     * Username of HR who created the job.
     * Used for notification purposes and auto-assignment.
     */
    private String createdBy;

    /**
     * When the job info was fetched (for snapshot timestamp).
     */
    @Builder.Default
    private Instant fetchedAt = Instant.now();
}
