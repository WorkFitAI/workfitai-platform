package org.workfitai.applicationservice.dto.response;

import java.time.Instant;

import org.workfitai.applicationservice.model.enums.ApplicationStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for application data.
 * 
 * This DTO is returned for:
 * - POST /applications (after successful creation)
 * - GET /applications/{id}
 * - GET /applications (as items in paginated list)
 * 
 * Includes job snapshot (captured at application time) and CV file info.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Application response containing application details")
public class ApplicationResponse {

    @NotBlank
    @Schema(description = "Unique identifier of the application", example = "6579a1b2c3d4e5f6a7b8c9d0")
    private String id;

    @NotBlank
    @Schema(description = "Username of the applicant (from JWT sub)", example = "candidate_john")
    private String username;

    @NotBlank
    @Schema(description = "ID of the job applied to", example = "550e8400-e29b-41d4-a716-446655440000")
    private String jobId;

    // CV File Info

    @NotBlank
    @Schema(description = "URL to the CV file in MinIO storage", example = "http://minio:9000/cvs-files/user/app123/resume.pdf")
    private String cvFileUrl;

    @NotBlank
    @Schema(description = "Original filename of the uploaded CV", example = "John_Doe_Resume.pdf")
    private String cvFileName;

    @Schema(description = "Content type of the CV file", example = "application/pdf")
    private String cvContentType;

    @Schema(description = "Size of the CV file in bytes", example = "245678")
    private Long cvFileSize;

    // Application Status and Content

    @NotNull
    @Schema(description = "Current status of the application", example = "APPLIED")
    private ApplicationStatus status;

    @Schema(description = "Cover letter from the applicant", example = "I am excited about this opportunity...")
    private String coverLetter;

    // Timestamps

    @NotNull
    @Schema(description = "Timestamp when the application was submitted", example = "2024-01-15T10:30:00Z")
    private Instant createdAt;

    @Schema(description = "Timestamp of the last status update", example = "2024-01-16T14:45:00Z")
    private Instant updatedAt;

    // Job Snapshot (captured at application time)

    @Schema(description = "Job snapshot captured at application time")
    private JobSnapshotResponse jobSnapshot;

    // Phase 3: Assignment \u0026 Company Fields

    @Schema(description = "Company ID for the application", example = "company-123")
    private String companyId;

    @Schema(description = "HR username assigned to this application", example = "hr_sarah")
    private String assignedTo;

    @Schema(description = "When the application was assigned", example = "2024-01-16T09:00:00Z")
    private Instant assignedAt;

    @Schema(description = "Manager who assigned the application", example = "manager_tom")
    private String assignedBy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Snapshot of job info at application time")
    public static class JobSnapshotResponse {

        @Schema(description = "Job title", example = "Senior Java Developer")
        private String title;

        @Schema(description = "Company name", example = "TechCorp Inc")
        private String companyName;

        @Schema(description = "Job location", example = "Remote")
        private String location;

        @Schema(description = "Employment type", example = "FULL_TIME")
        private String employmentType;

        @Schema(description = "Experience level", example = "SENIOR")
        private String experienceLevel;

        @Schema(description = "When the snapshot was taken", example = "2024-01-15T10:30:00Z")
        private Instant snapshotAt;
    }
}
