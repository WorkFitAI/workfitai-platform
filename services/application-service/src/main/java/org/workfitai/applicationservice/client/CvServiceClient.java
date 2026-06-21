package org.workfitai.applicationservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.applicationservice.config.InternalFeignConfig;
import org.workfitai.applicationservice.dto.CvSnapshotResponse;

/**
 * Feign client for cv-service internal API.
 *
 * Used during the SNAPSHOT_CV saga step to:
 * 1. Send the candidate's CV PDF to cv-service for parsing.
 * 2. Receive structured fields (summary, experience, skills, education) back.
 *
 * The endpoint is internal-only (not routed through API Gateway).
 * URL is configured via {@code cv-service.internal-url} property (Docker: http://cv-service:8080).
 *
 * Note: Uses InternalFeignConfig to avoid forwarding JWT (internal endpoint is permitAll).
 */
@FeignClient(name = "cv")
public interface CvServiceClient {

    /**
     * Creates an immutable CV snapshot by parsing the uploaded PDF.
     *
     * @param username      candidate's username
     * @param applicationId temporary application UUID (assigned before DB save)
     * @param jobName       title of the job applied to — cv-service uses it to name the stored PDF object
     * @param cvPdfFile     the CV PDF file
     * @return structured CV fields extracted by cv-service
     */
    @PostMapping(
            value = "/internal/cvs/application-snapshot",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    CvSnapshotResponse createApplicationSnapshot(
            @RequestPart("username") String username,
            @RequestPart("applicationId") String applicationId,
            @RequestPart("jobName") String jobName,
            @RequestPart("cvPdfFile") MultipartFile cvPdfFile
    );
}
