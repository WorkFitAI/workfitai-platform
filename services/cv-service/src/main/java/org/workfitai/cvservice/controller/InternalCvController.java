package org.workfitai.cvservice.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.cvservice.model.dto.response.CvDataResponse;
import org.workfitai.cvservice.model.dto.response.CvSnapshotResponse;
import org.workfitai.cvservice.service.iCVService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service-to-service endpoint, not exposed via API Gateway.
 * Accessible only within the Docker network.
 * Used by:
 *   - recommendation-engine at startup to warm up CvReferStore (GET /internal/cvs/batch)
 *   - application-service during apply saga to extract CV fields  (POST /internal/cvs/application-snapshot)
 */
@RestController
@RequestMapping("/internal/cvs")
@RequiredArgsConstructor
@Slf4j
public class InternalCvController {

    private final iCVService cvService;

    /**
     * Batch CV data lookup by usernames — used by recommendation-engine cold-start sync.
     */
    @PostMapping("/batch")
    public ResponseEntity<List<CvDataResponse>> getCvDataBatch(@RequestBody List<String> usernames) {
        List<CvDataResponse> result = cvService.getCvDataBatch(usernames);
        log.info("Internal sync: returning CV data for {}/{} requested usernames", result.size(), usernames.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Creates an immutable CV snapshot for an application.
     *
     * Called by application-service during the SNAPSHOT_CV saga step.
     * Parses the uploaded PDF and persists a CV document with {@code applicationId} set.
     *
     * @param username      candidate's username (JWT sub from application-service context)
     * @param applicationId temporary UUID assigned by application-service before DB save
     * @param jobName       title of the job applied to — used to name the stored PDF object
     * @param cvPdfFile     the CV PDF file uploaded by the candidate
     * @return {@link CvSnapshotResponse} with cvId and extracted structured fields
     */
    @PostMapping(value = "/application-snapshot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CvSnapshotResponse> createApplicationSnapshot(
            @RequestPart("username") String username,
            @RequestPart("applicationId") String applicationId,
            @RequestPart("jobName") String jobName,
            @RequestPart("cvPdfFile") MultipartFile cvPdfFile) {

        log.info("Internal snapshot request: username={} applicationId={} jobName={} fileSize={}",
                username, applicationId, jobName, cvPdfFile.getSize());

        CvSnapshotResponse response = cvService.createApplicationSnapshot(username, applicationId, jobName, cvPdfFile);
        return ResponseEntity.ok(response);
    }

    /**
     * Batch CV data lookup by applicationIds — used for active-pool enrichment.
     *
     * Request body: list of applicationId strings
     * Returns CvSnapshotResponse for each found snapshot (missing ones are silently skipped).
     */
    @PostMapping("/batch-by-application-ids")
    public ResponseEntity<List<CvSnapshotResponse>> getCvSnapshotsByApplicationIds(
            @RequestBody List<String> applicationIds) {

        if (applicationIds == null || applicationIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<CvSnapshotResponse> results = cvService.getCvSnapshotsByApplicationIds(applicationIds);
        log.info("Internal batch-by-application-ids: found {}/{} snapshots",
                results.size(), applicationIds.size());
        return ResponseEntity.ok(results);
    }
}
