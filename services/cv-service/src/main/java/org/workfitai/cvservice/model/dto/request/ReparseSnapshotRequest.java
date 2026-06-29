package org.workfitai.cvservice.model.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /internal/cvs/reparse-snapshot.
 *
 * Used by application-service's reconciliation job to re-create a snapshot CV
 * for an application whose original SNAPSHOT_CV saga step failed (cvSnapshotId
 * is still null). Unlike /internal/cvs/application-snapshot, this does not take
 * a fresh multipart upload — it re-downloads the candidate's PDF from cvFileUrl,
 * which application-service already has on the Application record regardless of
 * whether the original snapshot call ever succeeded.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReparseSnapshotRequest {

    private String username;

    private String applicationId;

    private String jobName;

    private String cvFileUrl;
}
