package org.workfitai.applicationservice.dto.request;

import java.util.List;

import org.workfitai.applicationservice.model.enums.ApplicationStatus;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for bulk status update.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkUpdateRequest {

    /**
     * List of application IDs to update (max 100).
     */
    @NotEmpty(message = "Application IDs list cannot be empty")
    @Size(max = 100, message = "Cannot update more than 100 applications at once")
    private List<String> applicationIds;

    /**
     * New status to apply.
     */
    @NotNull(message = "Status is required")
    private ApplicationStatus status;

    /**
     * Optional reason for bulk update.
     */
    @Size(max = 500, message = "Reason must not exceed 500 characters")
    private String reason;
}
