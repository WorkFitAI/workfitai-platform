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
import org.workfitai.applicationservice.model.enums.ApplicationStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MongoDB document representing a job application.
 * 
 * An application links a User (candidate) to a Job with a specific CV.
 * 
 * Business constraints:
 * - One active application per (userId, jobId) pair (enforced by unique
 * compound index)
 * - userId must match the CV owner (validated in service layer)
 * - Job must be in PUBLISHED status (validated via Feign call)
 * 
 * Indexes:
 * - Compound unique index on (userId, jobId) to prevent duplicate applications
 * - Individual indexes on userId and jobId for efficient queries
 */
@Document(collection = "applications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "unique_user_job", def = "{'userId': 1, 'jobId': 1}", unique = true)
})
public class Application {

    /**
     * MongoDB auto-generated unique identifier.
     * Format: ObjectId string (24 hex characters)
     */
    @Id
    private String id;

    /**
     * ID of the user (candidate) who submitted the application.
     * Must match the owner of the CV being submitted.
     * Indexed for efficient queries like "get all applications by user".
     */
    @NotBlank(message = "userId is required")
    @Indexed
    private String userId;

    /**
     * ID of the job being applied to.
     * Must reference a job in PUBLISHED status.
     * Indexed for efficient queries like "get all applications for a job".
     */
    @NotBlank(message = "jobId is required")
    @Indexed
    private String jobId;

    /**
     * ID of the CV submitted with this application.
     * Must belong to the userId (validated in service layer).
     */
    @NotBlank(message = "cvId is required")
    private String cvId;

    /**
     * Current status of the application in the hiring pipeline.
     * Defaults to APPLIED when created.
     * 
     * @see ApplicationStatus for status flow documentation
     */
    @NotNull(message = "status is required")
    @Builder.Default
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    /**
     * Optional note or cover letter from the applicant.
     * Can be used for additional context or motivation.
     */
    private String note;

    // ==================== Audit Fields ====================
    // These fields are automatically populated by Spring Data MongoDB auditing

    /**
     * Timestamp when the application was first created.
     * Set automatically by @EnableMongoAuditing.
     */
    @CreatedDate
    private Instant createdAt;

    /**
     * Timestamp of the last modification.
     * Updated automatically by @EnableMongoAuditing.
     */
    @LastModifiedDate
    private Instant updatedAt;

    /**
     * User ID of who created this application.
     * Typically same as userId for self-applications.
     */
    @CreatedBy
    private String createdBy;

    /**
     * User ID of who last modified this application.
     * Typically a recruiter/admin when changing status.
     */
    @LastModifiedBy
    private String updatedBy;
}
