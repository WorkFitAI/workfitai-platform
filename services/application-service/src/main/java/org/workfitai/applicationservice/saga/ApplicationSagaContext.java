package org.workfitai.applicationservice.saga;

import org.workfitai.applicationservice.dto.CvSnapshotResponse;
import org.workfitai.applicationservice.dto.FileUploadResult;
import org.workfitai.applicationservice.dto.JobInfo;
import org.workfitai.applicationservice.model.Application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Context object that holds state throughout the Saga execution.
 * Passed between saga steps to accumulate results and track progress.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationSagaContext {

    // Input data
    private String username;
    private String email;
    private String jobId;
    private String coverLetter;

    /**
     * Pre-generated application ID passed to cv-service during SNAPSHOT_CV so that
     * CV.applicationId matches the saved Application._id once the document is persisted.
     * Must be set before the SNAPSHOT_CV step and reused in SAVE_APPLICATION.
     */
    private String applicationId;

    // Saga step results (accumulated as saga progresses)
    private JobInfo jobInfo;
    private FileUploadResult fileUploadResult;

    /**
     * CV snapshot response from cv-service.
     * Populated during SNAPSHOT_CV step; null when cv-service is unavailable.
     * Null value is acceptable — ranking still works with empty CV data (best-effort).
     */
    private CvSnapshotResponse cvSnapshot;

    private Application savedApplication;

    // Tracking
    private SagaStep currentStep;
    private boolean completed;
    private String errorMessage;

    /**
     * Saga steps in execution order.
     */
    public enum SagaStep {
        VALIDATE,        // Run validation pipeline
        FETCH_JOB_INFO,  // Get job details for snapshot
        UPLOAD_CV,       // Upload CV to MinIO
        SNAPSHOT_CV,     // Create CV snapshot in cv-service (best-effort)
        SAVE_APPLICATION, // Persist to MongoDB
        PUBLISH_EVENTS   // Fire Kafka events
    }
}
