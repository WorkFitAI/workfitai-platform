package org.workfitai.applicationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
 * ALL OPERATIONS ARE AUDITED via AuditAspect AOP
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationMapper applicationMapper;
    private final MongoTemplate mongoTemplate;

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
     * Get all applications with optional filters.
     * Admin sees everything including soft-deleted by default (includeDeleted=true).
     */
    public Page<ApplicationResponse> getApplications(
            ApplicationStatus status,
            String companyId,
            String username,
            boolean includeDeleted,
            Pageable pageable
    ) {
        Query query = new Query();

        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        if (companyId != null && !companyId.isBlank()) {
            query.addCriteria(Criteria.where("companyId").is(companyId));
        }
        if (username != null && !username.isBlank()) {
            query.addCriteria(Criteria.where("username").is(username));
        }
        if (!includeDeleted) {
            query.addCriteria(Criteria.where("deletedAt").isNull());
        }

        long total = mongoTemplate.count(query, Application.class);
        query.with(pageable);
        List<Application> applications = mongoTemplate.find(query, Application.class);

        List<ApplicationResponse> responses = applications.stream()
                .map(applicationMapper::toResponse)
                .toList();

        return new PageImpl<>(responses, pageable, total);
    }

    /**
     * Soft-delete an application: sets deletedAt/deletedBy and changes status to WITHDRAWN.
     */
    @Transactional
    public ApplicationResponse softDeleteApplication(String id, String adminUsername, String reason) {
        log.warn("ADMIN: Soft-deleting application id={}, by={}, reason={}", id, adminUsername, reason);

        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Application not found: " + id));

        if (application.getDeletedAt() != null) {
            throw new IllegalStateException("Application is already deleted: " + id);
        }

        ApplicationStatus previousStatus = application.getStatus();

        application.setDeletedAt(Instant.now());
        application.setDeletedBy(adminUsername);
        application.setStatus(ApplicationStatus.WITHDRAWN);
        application.setUpdatedAt(Instant.now());

        Application.StatusChange statusChange = Application.StatusChange.builder()
                .previousStatus(previousStatus)
                .newStatus(ApplicationStatus.WITHDRAWN)
                .changedBy(adminUsername)
                .changedAt(Instant.now())
                .reason(reason)
                .build();
        application.getStatusHistory().add(statusChange);

        Application saved = applicationRepository.save(application);
        log.info("ADMIN: Application soft-deleted id={}", saved.getId());
        return applicationMapper.toResponse(saved);
    }

    /**
     * Restore an admin-deleted application: clears soft-delete markers
     * and restores the status prior to the admin's WITHDRAWN change.
     */
    @Transactional
    public ApplicationResponse restoreApplication(String id) {
        log.warn("ADMIN: Restoring application id={}", id);

        Application application = applicationRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Application not found: " + id));

        if (application.getDeletedAt() == null) {
            throw new IllegalStateException("Application is not deleted: " + id);
        }

        application.setDeletedAt(null);
        application.setDeletedBy(null);
        application.setUpdatedAt(Instant.now());

        // Find the status before the admin-applied WITHDRAWN from history
        ApplicationStatus restoreStatus = ApplicationStatus.APPLIED;
        List<Application.StatusChange> history = application.getStatusHistory();
        if (history != null) {
            for (int i = history.size() - 1; i >= 0; i--) {
                Application.StatusChange change = history.get(i);
                if (change.getNewStatus() == ApplicationStatus.WITHDRAWN && change.getPreviousStatus() != null) {
                    restoreStatus = change.getPreviousStatus();
                    break;
                }
            }
        }

        application.setStatus(restoreStatus);
        Application.StatusChange statusChange = Application.StatusChange.builder()
                .previousStatus(ApplicationStatus.WITHDRAWN)
                .newStatus(restoreStatus)
                .changedBy("ADMIN")
                .changedAt(Instant.now())
                .reason("Restored by admin")
                .build();
        application.getStatusHistory().add(statusChange);

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

        applicationRepository.delete(application);

        log.warn("ADMIN: Application permanently deleted id={}", id);
    }
}
