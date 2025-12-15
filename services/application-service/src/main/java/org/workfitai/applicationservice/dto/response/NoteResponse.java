package org.workfitai.applicationservice.dto.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for HR notes on an application.
 * Only includes notes marked as candidateVisible when accessed by candidates.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "HR note on application (candidate view)")
public class NoteResponse {

    @Schema(description = "Unique identifier for this note", example = "550e8400-e29b-41d4-a716-446655440000", required = true)
    private String id;

    @Schema(description = "Username of the note author", example = "hr_sarah", required = true)
    private String author;

    @Schema(description = "Note content", example = "Great background in Java. Moving to interview.", required = true)
    private String content;

    @Schema(description = "When the note was created", example = "2024-01-16T14:35:00Z", required = true)
    private Instant createdAt;

    @Schema(description = "When the note was last updated", example = "2024-01-16T15:00:00Z")
    private Instant updatedAt;

    @Schema(description = "Whether this note is visible to candidates", example = "true")
    private boolean candidateVisible;
}
