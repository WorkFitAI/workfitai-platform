package org.workfitai.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRequest {
    @NotBlank(message = "Approver/Rejector username is required")
    private String approvedBy;

    private String rejectedBy;

    private String reason; // For rejection
}