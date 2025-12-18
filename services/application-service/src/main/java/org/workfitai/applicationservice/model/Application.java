package org.workfitai.applicationservice.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
import jakarta.validation.constraints.Email;
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
        @CompoundIndex(name = "unique_user_job", def = "{'username': 1, 'jobId': 1, 'deletedAt': 1}", unique = true),
        @CompoundIndex(name = "username_isDraft", def = "{'username': 1, 'isDraft': 1}"),
        @CompoundIndex(name = "company_status", def = "{'companyId': 1, 'status': 1, 'deletedAt': 1}"),
        @CompoundIndex(name = "company_assigned", def = "{'companyId': 1, 'assignedTo': 1, 'deletedAt': 1}"),
        @CompoundIndex(name = "assigned_draft_deleted", def = "{'assignedTo': 1, 'isDraft': 1, 'deletedAt': 1}"),
        @CompoundIndex(name = "assigned_status_draft_deleted", def = "{'assignedTo': 1, 'status': 1, 'isDraft': 1, 'deletedAt': 1}")
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
     * Email of the candidate who submitted the application.
     * Stored for notification purposes and quick access without Feign call.
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Indexed
    private String email;

    /**
     * ID of the job being applied to.
     * Must reference a job in PUBLISHED status.
     * Indexed for efficient queries like "get all applications for a job".
     */
    @NotBlank(message = Messages.Validation.JOB_ID_REQUIRED)
    @Size(max = 100, message = "Job ID cannot exceed 100 characters")
    @Indexed
    private String jobId;

    // ==================== Company \u0026 Assignment Fields (Phase 3)
    // ====================

    /**
     * Company ID for the application.
     * Derived from job.companyId or stored directly for performance.
     * Used for company-wide filtering and access control.
     * Nullable for backward compatibility (will be backfilled later).
     */
    @Indexed
    private String companyId;

    /**
     * Username of the HR user assigned to this application.
     * Used for workload distribution and assignment tracking.
     * Null means unassigned (in the common pool).
     */
    @Indexed
    private String assignedTo;

    /**
     * Timestamp when the application was assigned to an HR user.
     */
    private Instant assignedAt;

    /**
     * Username of the manager who performed the assignment.
     * Tracks who assigned the application for audit purposes.
     */
    private String assignedBy;

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

    // ==================== Draft Workflow Fields ====================

    /**
     * Indicates if this is a draft application (not yet submitted).
     * Draft applications skip Saga orchestration and don't trigger events.
     * Defaults to false for backward compatibility.
     */
    @Builder.Default
    private boolean isDraft = false;

    /**
     * Timestamp when the draft was submitted and became an active application.
     * Null for draft applications, set when draft is submitted.
     */
    private Instant submittedAt;

    // ==================== Soft Delete Fields ====================

    /**
     * Timestamp when the application was withdrawn/deleted.
     * Null for active applications.
     * Used for soft delete pattern.
     */
    private Instant deletedAt;

    /**
     * Username of who deleted/withdrew the application.
     * Typically the candidate who withdrew, or an admin who deleted.
     */
    private String deletedBy;

    // ==================== Status History ====================

    /**
     * Timeline of status changes for transparency.
     * Each entry records the transition, who made it, and when.
     */
    @Builder.Default
    private List<StatusChange> statusHistory = new ArrayList<>();

    // ==================== HR Notes ====================

    /**
     * Notes added by HR/recruiters.
     * Some notes can be marked as visible to candidates.
     */
    @Builder.Default
    private List<Note> notes = new ArrayList<>();

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
        private String postId;
        private String title;
        private String shortDescription;
        private String description;
        private String employmentType;
        private String experienceLevel;
        private String educationLevel;
        private String requiredExperience;
        private Double salaryMin;
        private Double salaryMax;
        private String currency;
        private String location;
        private Integer quantity;
        private Integer totalApplications;
        private Instant createdDate;
        private Instant lastModifiedDate;
        private Instant expiresAt;
        private String status;
        private List<String> skillNames;
        private String bannerUrl;
        private String createdBy;

        // Company info
        private String companyNo;
        private String companyName;
        private String companyDescription;
        private String companyAddress;
        private String companyWebsiteUrl;
        private String companyLogoUrl;
        private String companySize;

        private Instant snapshotAt;
    }

    // ==================== Embedded Document for Status Change ====================

    /**
     * Embedded document tracking a single status transition.
     * Used to build a complete audit trail of application progress.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StatusChange {
        /**
         * Status before this change (null for initial APPLIED status).
         */
        private ApplicationStatus previousStatus;

        /**
         * New status after this change.
         */
        @NotNull
        private ApplicationStatus newStatus;

        /**
         * Username of who made the change.
         */
        @NotBlank
        private String changedBy;

        /**
         * When the change occurred.
         */
        @NotNull
        private Instant changedAt;

        /**
         * Optional reason for the status change.
         */
        @Size(max = 500)
        private String reason;
    }

    // ==================== Embedded Document for HR Notes ====================

    /**
     * Embedded document for HR/recruiter notes on the application.
     * Notes can be internal or shared with the candidate.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Note {
        /**
         * Unique identifier for this note.
         */
        @NotBlank
        @Builder.Default
        private String id = java.util.UUID.randomUUID().toString();

        /**
         * Username of the note author (HR/recruiter).
         */
        @NotBlank
        private String author;

        /**
         * Note content.
         */
        @NotBlank
        @Size(max = 2000)
        private String content;

        /**
         * Whether this note should be visible to the candidate.
         * False for internal HR notes.
         */
        @Builder.Default
        private boolean candidateVisible = false;

        /**
         * When the note was created.
         */
        @NotNull
        private Instant createdAt;

        /**
         * When the note was last updated (null if never updated).
         */
        private Instant updatedAt;
    }
}
