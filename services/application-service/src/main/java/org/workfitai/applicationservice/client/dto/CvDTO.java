package org.workfitai.applicationservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * DTO representing CV data from cv-service.
 * 
 * Used for:
 * - Validating CV ownership (belongTo must match userId)
 * - Storing CV reference in Application
 * - Display purposes in application history
 * 
 * Key validation: belongTo field must equal the applying user's ID
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvDTO {

    /**
     * Unique CV identifier.
     */
    private String cvId;

    /**
     * CV headline/title.
     */
    private String headline;

    /**
     * CV summary text.
     */
    private String summary;

    /**
     * URL to the PDF version of the CV.
     */
    private String pdfUrl;

    /**
     * User ID who owns this CV.
     * 
     * CRITICAL: This field is used to verify CV ownership.
     * Applying user's ID must match this value.
     */
    private String belongTo;

    /**
     * Whether the CV is active (not deleted).
     */
    private boolean isExist;

    /**
     * CV creation timestamp.
     */
    private Instant createdAt;

    /**
     * Last update timestamp.
     */
    private Instant updatedAt;

    /**
     * Checks if this CV belongs to the specified user.
     * 
     * @param userId User ID to check ownership against
     * @return true if CV belongs to this user
     */
    public boolean belongsToUser(String userId) {
        return belongTo != null && belongTo.equals(userId);
    }
}
