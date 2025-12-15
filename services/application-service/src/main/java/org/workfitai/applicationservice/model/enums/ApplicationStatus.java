package org.workfitai.applicationservice.model.enums;

/**
 * Enum representing the lifecycle status of a job application.
 *
 * Flow: DRAFT → APPLIED → REVIEWING → INTERVIEW → OFFER → HIRED
 *                ↓        ↓           ↓
 *              REJECTED REJECTED   REJECTED
 *                ↓
 *            WITHDRAWN
 *
 * Business rules:
 * - Draft applications are not yet submitted (isDraft=true)
 * - New applications start with APPLIED status after submission
 * - Only recruiters/admins can change status (except WITHDRAWN)
 * - HIRED, REJECTED, and WITHDRAWN are terminal states
 */
public enum ApplicationStatus {

    /**
     * Draft state: Application not yet submitted.
     * Candidate can edit and submit later.
     * Does not trigger Saga workflow.
     */
    DRAFT,

    /**
     * Initial status when a candidate submits an application.
     * The application is waiting to be reviewed by a recruiter.
     */
    APPLIED,

    /**
     * Recruiter is actively reviewing the application.
     * CV and candidate profile are being evaluated.
     */
    REVIEWING,

    /**
     * Candidate has been invited for an interview.
     * This could be phone screen, technical, or on-site.
     */
    INTERVIEW,

    /**
     * Company has extended a job offer to the candidate.
     * Waiting for candidate's response.
     */
    OFFER,

    /**
     * Terminal state: Candidate accepted the offer and was hired.
     * Application process is complete with positive outcome.
     */
    HIRED,

    /**
     * Terminal state: Application was rejected at any stage.
     * No further actions possible on this application.
     */
    REJECTED,

    /**
     * Terminal state: Candidate withdrew their application.
     * Uses soft delete pattern (deletedAt timestamp set).
     */
    WITHDRAWN
}
