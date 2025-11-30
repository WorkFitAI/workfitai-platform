package org.workfitai.applicationservice.dto.response;

import java.time.Instant;

import org.workfitai.applicationservice.model.enums.ApplicationStatus;

import io.swagger.v3.oas.annotations.media.Schema;
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
 * Excludes sensitive audit fields like createdBy/updatedBy
 * unless specifically needed for admin views.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Application response containing application details")
public class ApplicationResponse {

    @Schema(description = "Unique identifier of the application", example = "6579a1b2c3d4e5f6a7b8c9d0")
    private String id;

    @Schema(description = "ID of the applicant", example = "user-123")
    private String userId;

    @Schema(description = "ID of the job applied to", example = "550e8400-e29b-41d4-a716-446655440000")
    private String jobId;

    @Schema(description = "ID of the submitted CV", example = "cv-abc-123")
    private String cvId;

    @Schema(description = "Current status of the application", example = "APPLIED")
    private ApplicationStatus status;

    @Schema(description = "Optional cover letter or notes", example = "I am excited about this opportunity...")
    private String note;

    @Schema(description = "Timestamp when the application was submitted", example = "2024-01-15T10:30:00Z")
    private Instant createdAt;

    @Schema(description = "Timestamp of the last status update", example = "2024-01-16T14:45:00Z")
    private Instant updatedAt;

    // ═══════════════════════════════════════════════════════════════════════
    // Enrichment fields (populated from external services)
    // ═══════════════════════════════════════════════════════════════════════

    @Schema(description = "Job title (enriched from job-service)", example = "Senior Java Developer")
    private String jobTitle;

    @Schema(description = "Company name (enriched from job-service)", example = "TechCorp Inc")
    private String companyName;

    @Schema(description = "CV headline (enriched from cv-service)", example = "Experienced Full-Stack Developer")
    private String cvHeadline;
}
