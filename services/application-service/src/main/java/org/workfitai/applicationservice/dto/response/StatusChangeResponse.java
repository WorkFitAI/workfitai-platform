package org.workfitai.applicationservice.dto.response;

import java.time.Instant;

import org.workfitai.applicationservice.model.enums.ApplicationStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for a single status change entry in the application history.
 * Used to provide transparency to candidates about application progress.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Status change history entry")
public class StatusChangeResponse {

    @Schema(description = "Previous status before this change (null for initial status)", example = "APPLIED")
    private ApplicationStatus previousStatus;

    @Schema(description = "New status after this change", example = "REVIEWING", required = true)
    private ApplicationStatus newStatus;

    @Schema(description = "Username of who made the change", example = "hr_sarah", required = true)
    private String changedBy;

    @Schema(description = "When the change occurred", example = "2024-01-16T14:30:00Z", required = true)
    private Instant changedAt;

    @Schema(description = "Optional reason for the status change", example = "Candidate meets all requirements")
    private String reason;
}
