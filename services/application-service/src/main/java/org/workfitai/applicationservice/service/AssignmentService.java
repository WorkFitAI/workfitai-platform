package org.workfitai.applicationservice.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.exception.BadRequestException;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.repository.ApplicationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for application assignment workflow.
 *
 * Handles:
 * - Assigning applications to HR users
 * - Unassigning applications (return to pool)
 * - Re-assignment workflow
 *
 * Business Rules:
 * - Assignee must be HR user in same company (validation at controller level)
 * - Assignment tracked for audit (assignedBy, assignedAt)
 * - Notification sent to assignee (log only in Phase 3, Kafka in Phase 4)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationMapper applicationMapper;

    /**
     * Assign application to HR user.
     *
     * @param applicationId Application to assign
     * @param assignedTo    HR username to assign to
     * @param assignedBy    Manager performing the assignment
     * @return Updated application response
     */
    @Transactional
    public ApplicationResponse assignApplication(
            String applicationId,
            String assignedTo,
            String assignedBy) {

        log.info("Assigning application {} to HR: {}, by: {}", applicationId, assignedTo, assignedBy);

        // Fetch application
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found: " + applicationId));

        // Validation: Cannot assign draft applications
        if (application.isDraft()) {
            throw new BadRequestException("Cannot assign draft applications. Submit draft first.");
        }

        // Check if already assigned to same user
        if (assignedTo.equals(application.getAssignedTo())) {
            log.warn("Application {} already assigned to {}", applicationId, assignedTo);
            throw new BadRequestException("Application already assigned to " + assignedTo);
        }

        // Update assignment fields
        application.setAssignedTo(assignedTo);
        application.setAssignedAt(Instant.now());
        application.setAssignedBy(assignedBy);

        // Save
        Application updated = applicationRepository.save(application);

        // TODO Phase 4: Send Kafka event for notification
        log.info("Assignment notification: applicationId={}, assignedTo={}", applicationId, assignedTo);

        return applicationMapper.toResponse(updated);
    }

    /**
     * Unassign application (return to pool).
     *
     * @param applicationId Application to unassign
     * @param unassignedBy  Manager performing unassignment
     * @return Updated application response
     */
    @Transactional
    public ApplicationResponse unassignApplication(
            String applicationId,
            String unassignedBy) {

        log.info("Unassigning application: {}, by: {}", applicationId, unassignedBy);

        // Fetch application
        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found: " + applicationId));

        // Check if currently assigned
        if (application.getAssignedTo() == null) {
            throw new BadRequestException("Application is not assigned to anyone");
        }

        // Clear assignment fields
        String previouslyAssignedTo = application.getAssignedTo();
        application.setAssignedTo(null);
        application.setAssignedAt(null);
        application.setAssignedBy(null);

        // Save
        Application updated = applicationRepository.save(application);

        log.info("Application unassigned: {} (previously: {})", applicationId, previouslyAssignedTo);

        return applicationMapper.toResponse(updated);
    }

    /**
     * Re-assign application to different HR.
     *
     * @param applicationId Application to re-assign
     * @param newAssignedTo New HR username
     * @param assignedBy    Manager performing re-assignment
     * @return Updated application response
     */
    @Transactional
    public ApplicationResponse reassignApplication(
            String applicationId,
            String newAssignedTo,
            String assignedBy) {

        log.info("Re-assigning application {} from current to {}", applicationId, newAssignedTo);

        // Use same logic as assign (will update existing assignment)
        return assignApplication(applicationId, newAssignedTo, assignedBy);
    }
}
