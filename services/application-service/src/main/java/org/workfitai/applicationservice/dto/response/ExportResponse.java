package org.workfitai.applicationservice.dto.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for export operations.
 *
 * Phase 3: Simplified synchronous export
 * - Contains direct download URL
 * - No export status tracking
 *
 * Phase 4/5: Async export with status tracking
 * - exportId for polling status
 * - status: PROCESSING, COMPLETED, FAILED
 * - downloadUrl populated when COMPLETED
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExportResponse {

    /**
     * Export ID (for async processing in future phases).
     * Phase 3: Can be null (synchronous export).
     */
    private String exportId;

    /**
     * Export format (csv, xlsx).
     */
    private String format;

    /**
     * Number of rows exported.
     */
    private Long rowCount;

    /**
     * File size in bytes.
     */
    private Long fileSize;

    /**
     * Download URL for the exported file.
     * Phase 3: Direct file download (no MinIO pre-signed URL).
     * Phase 4/5: MinIO pre-signed URL with expiration.
     */
    private String downloadUrl;

    /**
     * When the export was generated.
     */
    private Instant generatedAt;

    /**
     * Expiration time for download URL (Phase 4/5).
     */
    private Instant expiresAt;
}
