package org.workfitai.applicationservice.dto.request;

import org.workfitai.applicationservice.constants.Messages;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new job application.
 * 
 * Validation rules:
 * - jobId and cvId are required (NotBlank)
 * - note is optional but limited to 2000 characters
 * - username is extracted from JWT sub claim (not in request body)
 * - jobId must reference a PUBLISHED job (validated in service via Feign)
 * - cvId must belong to username (validated in service via Feign)
 * 
 * Example request body:
 * 
 * <pre>
 * {
 *   "jobId": "job-456",
 *   "cvId": "cv-789",
 *   "note": "I'm excited about this opportunity..."
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for submitting a job application")
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
     * ID of the CV to submit with this application.
     * The CV must belong to the authenticated user.
     */
    @NotBlank(message = Messages.Validation.CV_ID_REQUIRED)
    @Size(max = 100, message = "CV ID cannot exceed 100 characters")
    @Schema(description = "ID of the CV to submit (must belong to the applicant)", example = "cv-abc-123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cvId;

    /**
     * Optional cover letter or additional notes.
     * Can be used to provide context or express motivation.
     */
    @Size(max = 2000, message = Messages.Validation.NOTE_MAX_LENGTH)
    @Schema(description = "Optional cover letter or additional notes (max 2000 characters)", example = "I am excited about this opportunity because...", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String note;
}
