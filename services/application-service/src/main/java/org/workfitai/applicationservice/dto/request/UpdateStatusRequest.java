package org.workfitai.applicationservice.dto.request;

import org.workfitai.applicationservice.constants.Messages;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating application status.
 * 
 * Used by HR/Admin to move applications through the hiring pipeline.
 * 
 * Example request body:
 * 
 * <pre>
 * {
 *   "status": "INTERVIEW",
 *   "note": "Scheduled for technical interview on Monday"
 * }
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request payload for updating application status")
public class UpdateStatusRequest {

    /**
     * The new status for the application.
     */
    @NotNull(message = Messages.Validation.STATUS_REQUIRED)
    @Schema(description = "New status for the application", example = "INTERVIEW", requiredMode = Schema.RequiredMode.REQUIRED)
    private ApplicationStatus status;

    /**
     * Optional note explaining the status change.
     * Useful for providing feedback or next steps.
     */
    @Size(max = 500, message = "Note cannot exceed 500 characters")
    @Schema(description = "Optional note explaining the status change", example = "Scheduled for technical interview", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String note;
}
