package org.workfitai.applicationservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;

import java.time.Instant;
import java.util.List;

/**
 * Admin request to manually create an application
 * Bypasses normal Saga workflow for data migration/support
 */
public record AdminCreateApplicationRequest(
    @NotBlank(message = "Username is required")
    String username,

    @Email(message = "Valid email is required")
    @NotBlank(message = "Email is required")
    String email,

    @NotBlank(message = "Job ID is required")
    String jobId,

    ApplicationStatus status, // Can be any status

    String cvFileUrl, // Pre-uploaded to MinIO

    String coverLetter,

    List<NoteInput> notes,

    Instant createdAt, // Override timestamp

    String reason // Reason for manual creation (audit trail)
) {
    /**
     * Nested DTO for initial notes
     */
    public record NoteInput(
        String author,
        String content
    ) {}
}
