package org.workfitai.applicationservice.model;

import java.time.Instant;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.workfitai.applicationservice.constants.Messages;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MongoDB document representing a job application.
 * 
 * An application links a User (candidate) to a Job with an uploaded CV file.
 * Job info is snapshotted at application time to preserve historical context.
 * 
 * Business constraints:
 * - One active application per (username, jobId) pair (enforced by unique
 * compound index)
 * - CV file is stored in MinIO, URL stored here
 * - Job must be in PUBLISHED status (validated via Saga validation step)
 * 
 * Indexes:
 * - Compound unique index on (username, jobId) to prevent duplicate
 * applications
 * - Individual indexes on username and jobId for efficient queries
 */
@Document(collection = "applications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "unique_user_job", def = "{'username': 1, 'jobId': 1}", unique = true)
})
public class Application {

    /**
     * MongoDB auto-generated unique identifier.
     * Format: ObjectId string (24 hex characters)
     */
    @Id
    private String id;

    /**
     * Username of the candidate who submitted the application.
     * This is the JWT "sub" claim (e.g., "candidate_john").
     * Indexed for efficient queries like "get all applications by user".
     */
    @NotBlank(message = Messages.Validation.USERNAME_REQUIRED)
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Indexed
    private String username;

    /**
     * ID of the job being applied to.
     * Must reference a job in PUBLISHED status.
     * Indexed for efficient queries like "get all applications for a job".
     */
    @NotBlank(message = Messages.Validation.JOB_ID_REQUIRED)
    @Size(max = 100, message = "Job ID cannot exceed 100 characters")
    @Indexed
    private String jobId;

    // ==================== Job Snapshot (captured at application time)
    // ====================

    /**
     * Snapshot of job info at time of application.
     * Preserved even if job is later modified or deleted.
     */
    private JobSnapshot jobSnapshot;

    // ==================== CV File Info (stored in MinIO) ====================

    /**
     * URL to the CV file in MinIO storage.
     * Format: http://minio:9000/cvs-files/{username}/{applicationId}/{filename}
     */
    @NotBlank(message = "CV file URL is required")
    private String cvFileUrl;

    /**
     * Original filename of the uploaded CV.
     * Preserved for display and download purposes.
     */
    @NotBlank(message = "CV filename is required")
    @Size(max = 255, message = "CV filename cannot exceed 255 characters")
    private String cvFileName;

    /**
     * Content type of the CV file (e.g., "application/pdf").
     */
    private String cvContentType;

    /**
     * Size of the CV file in bytes.
     */
    private Long cvFileSize;

    // ==================== Application Content ====================

    /**
     * Current status of the application in the hiring pipeline.
     * Defaults to APPLIED when created.
     * 
     * @see ApplicationStatus for status flow documentation
     */
    @NotNull(message = Messages.Validation.STATUS_REQUIRED)
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    /**
     * Cover letter from the applicant.
     * Explains motivation and fit for the position.
     */
    @Size(max = 5000, message = "Cover letter cannot exceed 5000 characters")
    private String coverLetter;

    // ==================== Audit Fields ====================

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @CreatedBy
    private String createdBy;

    /**
     * User ID of who last modified this application.
     * Typically a recruiter/admin when changing status.
     */
    @LastModifiedBy
    private String updatedBy;

    // ==================== Embedded Document for Job Snapshot ====================

    /**
     * Embedded document containing job information at application time.
     * This is a point-in-time snapshot - the actual job may have changed.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class JobSnapshot {
        private String title;
        private String companyName;
        private String location;
        private String employmentType;
        private String experienceLevel;
        private Instant snapshotAt;
    }
}
