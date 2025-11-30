package org.workfitai.applicationservice.client.dto;

/**
 * Mirror of job-service's JobStatus enum.
 * 
 * Only PUBLISHED jobs can receive applications.
 * 
 * Used for deserializing job-service responses and
 * business rule validation.
 */
public enum JobStatus {

    /**
     * Job is being drafted, not visible to candidates.
     */
    DRAFT,

    /**
     * Job is published and accepting applications.
     */
    PUBLISHED,

    /**
     * Job is closed, no longer accepting applications.
     */
    CLOSED
}
