package org.workfitai.applicationservice.dto.request;

import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for submitting a draft application.
 * CV file is required if not already uploaded in the draft.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request to submit a draft application")
public class SubmitDraftRequest {

    @Schema(description = "CV file (required if not already uploaded in draft)")
    private MultipartFile cvPdfFile;
}
