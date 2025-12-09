package org.workfitai.applicationservice.port.outbound;

import org.workfitai.applicationservice.dto.JobInfo;

/**
 * Outbound port for job-service communication.
 * Part of Hexagonal Architecture - defines interface for infrastructure
 * adapter.
 * 
 * Implementation: JobServiceAdapter (HTTP client to job-service)
 */
public interface JobServicePort {

    /**
     * Validate that a job exists and is in PUBLISHED status.
     *
     * @param jobId The job ID to validate
     * @return JobInfo containing job details for snapshot
     * @throws JobNotFoundException     if job doesn't exist
     * @throws JobNotPublishedException if job is not in PUBLISHED status
     */
    JobInfo validateAndGetJob(String jobId);

    /**
     * Check if a job exists (regardless of status).
     *
     * @param jobId The job ID to check
     * @return true if job exists
     */
    boolean jobExists(String jobId);
}
