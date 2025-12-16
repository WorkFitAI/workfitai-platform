package org.workfitai.applicationservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new note on an application.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateNoteRequest {

    /**
     * Note content (max 2000 characters).
     */
    @NotBlank(message = "Note content is required")
    @Size(max = 2000, message = "Note content must not exceed 2000 characters")
    private String content;

    /**
     * Whether this note should be visible to the candidate.
     * Defaults to false (internal note).
     */
    @Builder.Default
    private boolean candidateVisible = false;
}
