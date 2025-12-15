package org.workfitai.applicationservice.dto.request;

import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating a draft application.
 * All fields are optional - only provided fields will be updated.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to update a draft application")
public class UpdateDraftRequest {

    @Email(message = "Invalid email format")
    @Schema(description = "Updated email address", example = "newemail@example.com")
    private String email;

    @Size(max = 5000, message = "Cover letter cannot exceed 5000 characters")
    @Schema(description = "Updated cover letter", example = "I am very interested in this position...")
    private String coverLetter;

    @Schema(description = "Updated CV file (replaces existing file if any)")
    private MultipartFile cvPdfFile;
}
