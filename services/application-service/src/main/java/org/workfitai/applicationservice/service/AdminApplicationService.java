package org.workfitai.applicationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.applicationservice.dto.request.AdminCreateApplicationRequest;
import org.workfitai.applicationservice.dto.request.AdminOverrideRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.exception.NotFoundException;
import org.workfitai.applicationservice.mapper.ApplicationMapper;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for admin-level application operations
 * Bypasses normal validation and business rules
 * ALL OPERATIONS ARE AUDITED
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationMapper applicationMapper;
    private final AuditLogService auditLogService;

    /**
     * Manually create an application (bypasses Saga workflow)
     * Use cases: data migration, manual data entry, support fixes
     */
    @Transactional
    public ApplicationResponse createApplication(AdminCreateApplicationRequest request) {
        log.warn("ADMIN: Manual application creation for user={}, job={}, reason={}",
                request.username(), request.jobId(), request.reason());

        Application application = Application.builder()
                .username(request.username())
                .email(request.email())
                .jobId(request.jobId())
                .status(request.status() != null ? request.status() : ApplicationStatus.APPLIED)
                .cvFileUrl(request.cvFileUrl())
                .coverLetter(request.coverLetter())
                .isDraft(false)
                .submittedAt(request.createdAt() != null ? request.createdAt() : Instant.now())
                .createdAt(request.createdAt() != null ? request.createdAt() : Instant.now())
                .updatedAt(Instant.now())
                .statusHistory(new ArrayList<>())
                .notes(new ArrayList<>())
                .build();

        // Add initial notes if provided
        if (request.notes() != null && !request.notes().isEmpty()) {
            List<Application.Note> notes = request.notes().stream()
                    .map(noteInput -> Application.Note.builder()
                            .author(noteInput.author())
                            .content(noteInput.content())
                            .createdAt(Instant.now())
                            .build())
                    .toList();
            application.setNotes(notes);
        }

        // Add status history entry
        // TODO: Implement addStatusChange helper method in Application model
        Application.StatusChange statusChange = Application.StatusChange.builder()
                .previousStatus(null)
                .newStatus(application.getStatus())
                .changedBy("SYSTEM")
                .changedAt(Instant.now())
                .reason(request.reason())
                .build();
        application.getStatusHistory().add(statusChange);

        Application saved = applicationRepository.save(application);

        log.info("ADMIN: Application created successfully id={}", saved.getId());

        return applicationMapper.toResponse(saved);
    }

    /**
     * Override any application field (USE WITH CAUTION)
     * Bypasses all validation and business rules
     */
    @Transactional
    public ApplicationResponse overrideApplication(String id, AdminOverrideRequest request) {
        log.warn("ADMIN: Override application id={}, reason={}", id, request.reason());

        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Application not found: " + id));

        // Override fields if provided
        if (request.status() != null) {
            application.setStatus(request.status());
        }

        if (request.assignedTo() != null) {
            application.setAssignedTo(request.assignedTo());
            application.setAssignedAt(Instant.now());
        }

        if (request.cvFileUrl() != null) {
            application.setCvFileUrl(request.cvFileUrl());
        }

        if (request.companyId() != null) {
            application.setCompanyId(request.companyId());
        }

        if (request.isDraft() != null) {
            application.setDraft(request.isDraft());
        }

        if (request.submittedAt() != null) {
            application.setSubmittedAt(request.submittedAt());
        }

        if (request.updatedAt() != null) {
            application.setUpdatedAt(request.updatedAt());
        } else {
            application.setUpdatedAt(Instant.now());
        }

        if (request.deletedBy() != null) {
            application.setDeletedBy(request.deletedBy());
        }

        if (request.deletedAt() != null) {
            application.setDeletedAt(request.deletedAt());
        }

        // Custom fields (future extensibility)
        if (request.customFields() != null) {
            log.warn("ADMIN: Custom fields override requested but not implemented: {}",
                    request.customFields().keySet());
        }

        Application saved = applicationRepository.save(application);

        log.info("ADMIN: Application overridden successfully id={}", saved.getId());

        return applicationMapper.toResponse(saved);
    }

    /**
     * Get all applications (no company filter)
     * Admin-only global view
     */
    public Page<ApplicationResponse> getAllApplications(Pageable pageable) {
        Page<Application> applications = applicationRepository.findAll(pageable);
        return applications.map(applicationMapper::toResponse);
    }

    /**
     * Get soft-deleted applications
     */
    public Page<ApplicationResponse> getDeletedApplications(Pageable pageable) {
        Page<Application> deleted = applicationRepository.findByDeletedAtIsNotNull(pageable);
        return deleted.map(applicationMapper::toResponse);
    }

    /**
     * Restore a soft-deleted application
     */
    @Transactional
    public ApplicationResponse restoreApplication(String id) {
        log.warn("ADMIN: Restoring application id={}", id);

        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Application not found: " + id));

        if (application.getDeletedAt() == null) {
            throw new IllegalStateException("Application is not deleted: " + id);
        }

        // Restore application
        application.setDeletedAt(null);
        application.setDeletedBy(null);
        application.setUpdatedAt(Instant.now());

        // Restore to previous status (before WITHDRAWN)
        // If status is WITHDRAWN, restore to APPLIED
        if (application.getStatus() == ApplicationStatus.WITHDRAWN) {
            application.setStatus(ApplicationStatus.APPLIED);
            // TODO: Implement addStatusChange helper method in Application model
            Application.StatusChange statusChange = Application.StatusChange.builder()
                    .previousStatus(ApplicationStatus.WITHDRAWN)
                    .newStatus(ApplicationStatus.APPLIED)
                    .changedBy("ADMIN")
                    .changedAt(Instant.now())
                    .reason("Restored from deleted")
                    .build();
            application.getStatusHistory().add(statusChange);
        }

        Application saved = applicationRepository.save(application);

        log.info("ADMIN: Application restored successfully id={}", saved.getId());

        return applicationMapper.toResponse(saved);
    }

    /**
     * Permanently delete an application (hard delete)
     * Should only be used after retention period or for GDPR compliance
     */
    @Transactional
    public void permanentlyDeleteApplication(String id, String reason) {
        log.warn("ADMIN: Permanent deletion requested id={}, reason={}", id, reason);

        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Application not found: " + id));

        // Audit log before deletion
        auditLogService.logAction(
                "APPLICATION",
                id,
                "PERMANENTLY_DELETED",
                "ADMIN",
                null,
                null,
                java.util.Map.of("reason", reason, "warning", "HARD DELETE - DATA LOST"));

        applicationRepository.delete(application);

        log.warn("ADMIN: Application permanently deleted id={}", id);
    }
}
