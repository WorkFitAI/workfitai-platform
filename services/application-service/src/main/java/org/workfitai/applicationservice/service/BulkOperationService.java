package org.workfitai.applicationservice.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.applicationservice.dto.kafka.ApplicationStatusChangedEvent;
import org.workfitai.applicationservice.dto.request.BulkUpdateRequest;
import org.workfitai.applicationservice.dto.response.BulkUpdateResult;
import org.workfitai.applicationservice.exception.BadRequestException;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.port.outbound.EventPublisherPort;
import org.workfitai.applicationservice.repository.ApplicationRepository;
import org.workfitai.applicationservice.validation.StatusTransitionValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for bulk operations on applications.
 *
 * Supports:
 * - Bulk status updates with transaction support
 * - All-or-nothing behavior (rollback on any failure)
 * - Kafka event publishing for each successful update
 * - Detailed result tracking
 *
 * Business Rules:
 * - Maximum 100 applications per bulk operation
 * - All status transitions must be valid
 * - Transaction rolls back if any application fails
 *
 * Security:
 * - Only users with application:update permission can use
 * - All updates logged for audit trail
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulkOperationService {

    private final ApplicationRepository applicationRepository;
    private final StatusTransitionValidator statusTransitionValidator;
    private final EventPublisherPort eventPublisher;

    private static final int MAX_BULK_SIZE = 100;

    /**
     * Update status of multiple applications in a single transaction.
     *
     * @param request   Bulk update request with application IDs and new status
     * @param updatedBy Username performing the update
     * @return Result with success/failure details
     * @throws BadRequestException if validation fails
     */
    @Transactional
    public BulkUpdateResult bulkUpdateStatus(BulkUpdateRequest request, String updatedBy) {
        log.info("Starting bulk status update: applicationCount={}, newStatus={}, updatedBy={}",
                request.getApplicationIds().size(), request.getStatus(), updatedBy);

        // Validate request size
        if (request.getApplicationIds().size() > MAX_BULK_SIZE) {
            throw new BadRequestException(
                    "Cannot update more than " + MAX_BULK_SIZE + " applications at once");
        }

        List<BulkUpdateResult.ApplicationUpdateResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        // Process each application
        for (String applicationId : request.getApplicationIds()) {
            try {
                updateSingleApplication(applicationId, request.getStatus(), request.getReason(), updatedBy);
                results.add(BulkUpdateResult.ApplicationUpdateResult.builder()
                        .applicationId(applicationId)
                        .success(true)
                        .build());
                successCount++;
            } catch (Exception e) {
                log.error("Failed to update application {}: {}", applicationId, e.getMessage());
                results.add(BulkUpdateResult.ApplicationUpdateResult.builder()
                        .applicationId(applicationId)
                        .success(false)
                        .errorMessage(e.getMessage())
                        .build());
                failureCount++;

                // Rollback entire transaction on any failure (all-or-nothing)
                throw new BadRequestException(
                        "Bulk update failed for application " + applicationId + ": " + e.getMessage());
            }
        }

        log.info("Bulk update completed: success={}, failure={}", successCount, failureCount);

        return BulkUpdateResult.builder()
                .successCount(successCount)
                .failureCount(failureCount)
                .results(results)
                .build();
    }

    /**
     * Update a single application's status within the bulk operation.
     *
     * @param applicationId Application to update
     * @param newStatus     New status
     * @param reason        Optional reason for change
     * @param updatedBy     Username performing update
     * @throws NotFoundException   if application not found
     * @throws BadRequestException if status transition is invalid
     */
    private void updateSingleApplication(
            String applicationId,
            ApplicationStatus newStatus,
            String reason,
            String updatedBy) {

        Application application = applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found: " + applicationId));

        ApplicationStatus previousStatus = application.getStatus();

        // Validate status transition
        statusTransitionValidator.validateTransition(previousStatus, newStatus);

        // Update status
        application.setStatus(newStatus);

        // Add to status history
        Application.StatusChange statusChange = Application.StatusChange.builder()
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .changedBy(updatedBy)
                .changedAt(Instant.now())
                .reason(reason != null ? reason : "Bulk status update")
                .build();
        application.getStatusHistory().add(statusChange);

        // Save application
        applicationRepository.save(application);

        // Publish Kafka event (fire-and-forget)
        publishStatusChangedEvent(application, previousStatus, updatedBy);

        log.debug("Application {} updated: {} -> {}", applicationId, previousStatus, newStatus);
    }

    /**
     * Publish status changed event to Kafka.
     *
     * @param application    Updated application
     * @param previousStatus Previous status
     * @param updatedBy      Username who made the change
     */
    private void publishStatusChangedEvent(
            Application application,
            ApplicationStatus previousStatus,
            String updatedBy) {

        try {
            eventPublisher.publishStatusChanged(
                    ApplicationStatusChangedEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .eventType("STATUS_CHANGED")
                            .timestamp(Instant.now())
                            .data(ApplicationStatusChangedEvent.StatusChangeData.builder()
                                    .applicationId(application.getId())
                                    .username(application.getUsername())
                                    .jobId(application.getJobId())
                                    .previousStatus(previousStatus)
                                    .newStatus(application.getStatus())
                                    .changedBy(updatedBy)
                                    .changedAt(Instant.now())
                                    .build())
                            .build());
        } catch (Exception e) {
            // Fire-and-forget: log but don't fail the operation
            log.warn("Failed to publish status changed event for {}: {}", application.getId(), e.getMessage());
        }
    }
}
