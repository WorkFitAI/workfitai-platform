package org.workfitai.applicationservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for assigning an application to an HR user.
 *
 * Used by HR Managers to distribute workload among their team.
 *
 * Validation:
 * - assignedTo must be a valid username (3-50 chars)
 *
 * Business Rules:
 * - Assignee must be HR role in same company
 * - Manager must have application:assign permission
 * - Assignment generates notification to assignee
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignApplicationRequest {

    /**
     * Username of the HR user to assign the application to.
     * Must be an HR user in the same company.
     */
    @NotBlank(message = "Assigned HR username is required")
    @Size(min = 3, max = 50, message = "HR username must be between 3 and 50 characters")
    private String assignedTo;
}
