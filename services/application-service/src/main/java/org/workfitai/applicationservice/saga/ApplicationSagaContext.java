package org.workfitai.applicationservice.saga;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.workfitai.applicationservice.dto.FileUploadResult;
import org.workfitai.applicationservice.dto.JobInfo;
import org.workfitai.applicationservice.model.Application;

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
    private String jobId;
    private String coverLetter;

    // Saga step results (accumulated as saga progresses)
    private JobInfo jobInfo;
    private FileUploadResult fileUploadResult;
    private Application savedApplication;

    // Tracking
    private SagaStep currentStep;
    private boolean completed;
    private String errorMessage;

    /**
     * Saga steps in execution order.
     */
    public enum SagaStep {
        VALIDATE, // Run validation pipeline
        FETCH_JOB_INFO, // Get job details for snapshot
        UPLOAD_CV, // Upload CV to MinIO
        SAVE_APPLICATION, // Persist to MongoDB
        PUBLISH_EVENTS // Fire Kafka events
    }
}
