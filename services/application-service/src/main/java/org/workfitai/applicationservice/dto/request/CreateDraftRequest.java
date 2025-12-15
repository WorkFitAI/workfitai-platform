package org.workfitai.applicationservice.dto.request;

import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a draft application.
 * Draft applications can be created without a CV and don't trigger Saga orchestration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to create a draft application")
public class CreateDraftRequest {

    @NotBlank(message = "Job ID is required")
    @Size(max = 100, message = "Job ID cannot exceed 100 characters")
    @Schema(description = "ID of the job to apply for", example = "67890abcdef", required = true)
    private String jobId;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Schema(description = "Candidate's email address", example = "john@example.com", required = true)
    private String email;

    @Size(max = 5000, message = "Cover letter cannot exceed 5000 characters")
    @Schema(description = "Optional cover letter (can be added later)", example = "I am very interested in this position...")
    private String coverLetter;

    @Schema(description = "Optional CV file (can be uploaded later when submitting draft)")
    private MultipartFile cvPdfFile;
}
