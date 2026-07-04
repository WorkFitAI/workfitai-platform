package org.workfitai.applicationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mirror DTO matching cv-service's {@code ReparseSnapshotRequest}.
 *
 * Sent by {@link org.workfitai.applicationservice.service.CvSnapshotReconciliationService}
 * to re-create a snapshot for an application whose original SNAPSHOT_CV saga step
 * failed (cvSnapshotId still null) — cv-service re-downloads the PDF from cvFileUrl
 * instead of requiring a fresh multipart upload.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReparseSnapshotRequest {
    private String username;
    private String applicationId;
    private String jobName;
    private String cvFileUrl;
}
