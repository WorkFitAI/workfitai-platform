package org.workfitai.applicationservice.dto.request;

import org.springframework.web.multipart.MultipartFile;
import org.workfitai.applicationservice.constants.Messages;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new job application with CV file upload.
 * 
 * This request uses multipart/form-data to accept:
 * - jobId: ID of the job to apply for
 * - cvPdfFile: The CV document (PDF only, max 5MB)
 * - coverLetter: Optional cover letter text
 * 
 * Validation rules:
 * - jobId is required (NotBlank)
 * - cvPdfFile is required (NotNull), must be PDF, max 5MB
 * - coverLetter is optional but limited to 5000 characters
 * - username is extracted from JWT sub claim (not in request body)
 * 
 * The Saga orchestrator will:
 * 1. Validate job exists via HTTP call to job-service
 * 2. Upload CV to MinIO
 * 3. Save application with job snapshot
 * 4. Publish Kafka events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for submitting a job application with CV upload")
public class CreateApplicationRequest {

    /**
     * ID of the job to apply for.
     * The job must be in PUBLISHED status.
     */
    @NotBlank(message = Messages.Validation.JOB_ID_REQUIRED)
    @Size(max = 100, message = "Job ID cannot exceed 100 characters")
    @Schema(description = "ID of the job to apply for (must be PUBLISHED)", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String jobId;

    /**
     * CV file to upload (PDF only).
     * Max file size: 5MB
     * Accepted types: application/pdf
     */
    @NotNull(message = "CV file is required")
    @Schema(description = "CV document file (PDF only, max 5MB)", type = "string", format = "binary", requiredMode = Schema.RequiredMode.REQUIRED)
    private MultipartFile cvPdfFile;

    /**
     * Cover letter explaining motivation and fit for the position.
     * Optional but recommended for better application quality.
     */
    @Size(max = 5000, message = "Cover letter cannot exceed 5000 characters")
    @Schema(description = "Cover letter explaining your motivation (max 5000 characters)", example = "I am excited about this opportunity because...", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String coverLetter;
}
