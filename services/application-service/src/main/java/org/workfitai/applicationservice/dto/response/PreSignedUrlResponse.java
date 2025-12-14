package org.workfitai.applicationservice.dto.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for pre-signed URL generation.
 *
 * Contains a temporary download URL that expires after 1 hour.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreSignedUrlResponse {

    /**
     * Temporary download URL (valid for 1 hour).
     */
    private String url;

    /**
     * When the URL expires.
     */
    private Instant expiresAt;

    /**
     * Original filename for download.
     */
    private String fileName;

    /**
     * File size in bytes.
     */
    private Long fileSize;
}
