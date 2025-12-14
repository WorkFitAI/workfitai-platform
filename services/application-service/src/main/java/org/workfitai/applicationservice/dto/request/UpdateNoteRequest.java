package org.workfitai.applicationservice.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating an existing note.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateNoteRequest {

    /**
     * Updated note content (max 2000 characters).
     * Optional - if null, content is not updated.
     */
    @Size(max = 2000, message = "Note content must not exceed 2000 characters")
    private String content;

    /**
     * Updated visibility flag.
     * Optional - if null, visibility is not updated.
     */
    private Boolean candidateVisible;
}
