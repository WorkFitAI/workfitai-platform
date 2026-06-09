package org.workfitai.applicationservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.service.AdminApplicationService;

/**
 * Admin-only controller for application management.
 * All endpoints require ROLE_ADMIN.
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminApplicationService adminApplicationService;

    /**
     * Get all applications with optional filters (status, company, username).
     * Includes withdrawn and soft-deleted applications by default.
     * GET /admin/applications?page=0&size=50&status=APPLIED&companyId=xxx&username=xxx&includeDeleted=true
     */
    @GetMapping("/applications")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestResponse<Page<ApplicationResponse>>> getApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(required = false) String companyId,
            @RequestParam(required = false) String username,
            @RequestParam(defaultValue = "true") boolean includeDeleted
    ) {
        log.info("ADMIN: Fetching applications, page={}, size={}, status={}, companyId={}, username={}, includeDeleted={}",
                page, size, status, companyId, username, includeDeleted);

        Pageable pageable = PageRequest.of(page, size);
        Page<ApplicationResponse> applications = adminApplicationService.getApplications(
                status, companyId, username, includeDeleted, pageable);

        return ResponseEntity.ok(new RestResponse<>(200, "Applications fetched successfully", applications));
    }

    /**
     * Soft-delete an application: sets deletedAt, deletedBy, and changes status to WITHDRAWN.
     * DELETE /admin/applications/{id}?reason=xxx
     */
    @DeleteMapping("/applications/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestResponse<ApplicationResponse>> deleteApplication(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "Admin deleted") String reason
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String adminUsername = auth != null ? auth.getName() : "ADMIN";

        log.warn("ADMIN: Soft-deleting application id={}, by={}, reason={}", id, adminUsername, reason);

        ApplicationResponse application = adminApplicationService.softDeleteApplication(id, adminUsername, reason);
        return ResponseEntity.ok(new RestResponse<>(200, "Application deleted successfully", application));
    }

    /**
     * Restore an admin-deleted application: clears soft-delete markers and restores previous status.
     * PUT /admin/applications/{id}/restore
     */
    @PutMapping("/applications/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestResponse<ApplicationResponse>> restoreApplication(
            @PathVariable String id
    ) {
        log.warn("ADMIN: Restoring application id={}", id);
        ApplicationResponse application = adminApplicationService.restoreApplication(id);
        return ResponseEntity.ok(new RestResponse<>(200, "Application restored successfully", application));
    }
}
