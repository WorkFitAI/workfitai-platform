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
 * An application links a User (candidate) to a Job with a specific CV.
 * 
 * Business constraints:
 * - One active application per (username, jobId) pair (enforced by unique
 * compound index)
 * - username must match the CV owner (validated in service layer)
 * - Job must be in PUBLISHED status (validated via Feign call)
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

    /**
     * ID of the CV submitted with this application.
     * Must belong to the username (validated in service layer).
     */
    @NotBlank(message = Messages.Validation.CV_ID_REQUIRED)
    @Size(max = 100, message = "CV ID cannot exceed 100 characters")
    private String cvId;

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
     * Optional note or cover letter from the applicant.
     * Can be used for additional context or motivation.
     */
    @Size(max = 2000, message = Messages.Validation.NOTE_MAX_LENGTH)
    private String note;

    // Audit fields (populated by Spring Data MongoDB auditing)

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
}
