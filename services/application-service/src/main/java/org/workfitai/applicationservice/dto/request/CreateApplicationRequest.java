package org.workfitai.applicationservice.dto.request;

import org.workfitai.applicationservice.constants.ValidationMessages;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new job application.
 * 
 * Validation rules:
 * - All fields are required (NotBlank)
 * - userId must match authenticated user (validated in controller
 * via @PreAuthorize)
 * - jobId must reference a PUBLISHED job (validated in service via Feign)
 * - cvId must belong to userId (validated in service via Feign)
 * 
 * Example request body:
 * 
 * <pre>
 * {
 *   "userId": "user-123",
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
     * ID of the user submitting the application.
     * Must match the authenticated user's ID (enforced by security).
     */
    @NotBlank(message = ValidationMessages.APPLICATION_USER_ID_REQUIRED)
    @Schema(description = "ID of the applicant (must match authenticated user)", example = "user-123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userId;

    /**
     * ID of the job to apply for.
     * The job must be in PUBLISHED status.
     */
    @NotBlank(message = ValidationMessages.APPLICATION_JOB_ID_REQUIRED)
    @Schema(description = "ID of the job to apply for (must be PUBLISHED)", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String jobId;

    /**
     * ID of the CV to submit with this application.
     * The CV must belong to the userId.
     */
    @NotBlank(message = ValidationMessages.APPLICATION_CV_ID_REQUIRED)
    @Schema(description = "ID of the CV to submit (must belong to the applicant)", example = "cv-abc-123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cvId;

    /**
     * Optional cover letter or additional notes.
     * Can be used to provide context or express motivation.
     */
    @Schema(description = "Optional cover letter or additional notes", example = "I am excited about this opportunity because...", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String note;
}
