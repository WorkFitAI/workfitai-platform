package org.workfitai.applicationservice.dto.response;

import java.time.Instant;
import java.util.List;

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
    @Schema(description = "Email of the applicant", example = "candidate@example.com")
    private String email;

    @NotBlank
    @Schema(description = "ID of the job applied to", example = "550e8400-e29b-41d4-a716-446655440000")
    private String jobId;

    // CV File Info

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

        @Schema(description = "Job post ID", example = "140a5367-722c-40dd-ae71-c16bada74c74")
        private String postId;

        @Schema(description = "Job title", example = "Senior Java Developer")
        private String title;

        @Schema(description = "Short description", example = "Looking for a new position")
        private String shortDescription;

        @Schema(description = "Full job description")
        private String description;

        @Schema(description = "Employment type", example = "FULL_TIME")
        private String employmentType;

        @Schema(description = "Experience level", example = "SENIOR")
        private String experienceLevel;

        @Schema(description = "Education level", example = "Bachelor Degree in Computer Science")
        private String educationLevel;

        @Schema(description = "Required experience")
        private String requiredExperience;

        @Schema(description = "Minimum salary", example = "500.0")
        private Double salaryMin;

        @Schema(description = "Maximum salary", example = "800.0")
        private Double salaryMax;

        @Schema(description = "Salary currency", example = "USD")
        private String currency;

        @Schema(description = "Job location", example = "Ha Noi City")
        private String location;

        @Schema(description = "Number of positions", example = "10")
        private Integer quantity;

        @Schema(description = "Total applications", example = "1")
        private Integer totalApplications;

        @Schema(description = "Job creation date")
        private Instant createdDate;

        @Schema(description = "Last modified date")
        private Instant lastModifiedDate;

        @Schema(description = "Job expiration date")
        private Instant expiresAt;

        @Schema(description = "Job status", example = "PUBLISHED")
        private String status;

        @Schema(description = "Required skills", example = "[\"Java\", \"Spring Boot\"]")
        private List<String> skillNames;

        @Schema(description = "Banner image URL")
        private String bannerUrl;

        @Schema(description = "Job creator username", example = "jane")
        private String createdBy;

        // Company info
        @Schema(description = "Company ID", example = "b1c31a80-a9b9-4965-aeeb-0941db99a544")
        private String companyNo;

        @Schema(description = "Company name", example = "Sarah HR Manager's Company")
        private String companyName;

        @Schema(description = "Company description")
        private String companyDescription;

        @Schema(description = "Company address", example = "456 Corporate Ave, Ho Chi Minh City")
        private String companyAddress;

        @Schema(description = "Company website URL")
        private String companyWebsiteUrl;

        @Schema(description = "Company logo URL")
        private String companyLogoUrl;

        @Schema(description = "Company size")
        private String companySize;

        @Schema(description = "When the snapshot was taken", example = "2024-01-15T10:30:00Z")
        private Instant snapshotAt;
    }
}
