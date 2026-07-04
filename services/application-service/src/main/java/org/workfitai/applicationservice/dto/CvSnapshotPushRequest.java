package org.workfitai.applicationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pushed to recommendation-engine's internal {@code /internal/cv-refer/snapshot}
 * endpoint right after the reconciliation job successfully backfills a CV snapshot,
 * so the in-memory CvReferStore picks up the fix without waiting for the next
 * full startup resync.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CvSnapshotPushRequest {
    private String jobId;
    private String username;
    private String summary;
    private String experience;
    private String skills;
    private String education;
}
