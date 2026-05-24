package org.workfitai.applicationservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.applicationservice.dto.request.AdminCreateApplicationRequest;
import org.workfitai.applicationservice.dto.request.AdminOverrideRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.ExportResponse;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.dto.response.SystemStatsResponse;
import org.workfitai.applicationservice.service.AdminApplicationService;
import org.workfitai.applicationservice.service.ExportService;
import org.workfitai.applicationservice.service.RateLimitService;
import org.workfitai.applicationservice.service.SystemStatsService;

import java.time.Instant;
import java.util.List;

/**
 * Admin-only controller for system-level operations.
 * All endpoints require ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/v1/applications/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminApplicationService adminApplicationService;
    private final SystemStatsService systemStatsService;
    private final ExportService exportService;
    private final RateLimitService rateLimitService;

    /**
     * 1. Get all applications (no company filter)
     * GET /api/v1/applications/admin/all?page=0&size=50
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestResponse<Page<ApplicationResponse>>> getAllApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        log.info("ADMIN: Fetching all applications, page={}, size={}", page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<ApplicationResponse> applications = adminApplicationService.getAllApplications(pageable);

        return ResponseEntity.ok(new RestResponse<>(200, "All applications fetched successfully", applications));
    }

    /**
     * 3. Full data export (admin-only, includes deleted)
     * POST /api/v1/applications/admin/export
     * Rate limit: 5 exports per day per admin
     */
    @PostMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestResponse<ExportResponse>> exportAllApplications(
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate,
            @RequestBody(required = false) List<String> columns
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "unknown";

        if (!rateLimitService.isAllowed("admin-export", username, 5, 24)) {
            int remaining = rateLimitService.getRemainingRequests("admin-export", username, 5, 24);
            long resetHours = rateLimitService.getResetTimeSeconds("admin-export", username, 24) / 3600;

            log.warn("ADMIN: Export rate limit exceeded for user={}, remaining={}, resetIn={}h", username, remaining, resetHours);

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    new RestResponse<>(429, String.format("Export rate limit exceeded. Limit: 5 per day. Reset in %d hours.", resetHours))
            );
        }

        log.warn("ADMIN: Full platform export requested by user={}, includeDeleted={}", username, includeDeleted);

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            return ResponseEntity.badRequest().body(new RestResponse<>(400, "fromDate must be before toDate"));
        }

        ExportResponse export = exportService.exportAllApplications(includeDeleted, fromDate, toDate, columns);

        return ResponseEntity.ok(new RestResponse<>(200, "Export generated successfully", export));
    }

    /**
     * 4. System statistics
     * GET /api/v1/applications/admin/stats
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestResponse<SystemStatsResponse>> getSystemStats() {
        log.info("ADMIN: Fetching system statistics");
        return ResponseEntity.ok(new RestResponse<>(200, "System statistics fetched successfully", systemStatsService.getSystemStats()));
    }

    /**
     * 5. Manually create application (bypass Saga)
     * POST /api/v1/applications/admin/create
     */
    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestResponse<ApplicationResponse>> createApplication(
            @Valid @RequestBody AdminCreateApplicationRequest request
    ) {
        log.warn("ADMIN: Manual application creation requested for user={}, job={}", request.username(), request.jobId());

        if (request.reason() == null || request.reason().isBlank()) {
            return ResponseEntity.badRequest().body(new RestResponse<>(400, "Reason is required for admin manual creation"));
        }

        ApplicationResponse application = adminApplicationService.createApplication(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new RestResponse<>(201, "Application created successfully (manual creation)", application)
        );
    }

    /**
     * 6. Override any application field (USE WITH CAUTION)
     * PUT /api/v1/applications/admin/{id}/override
     */
    @PutMapping("/{id}/override")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestResponse<ApplicationResponse>> overrideApplication(
            @PathVariable String id,
            @Valid @RequestBody AdminOverrideRequest request
    ) {
        log.warn("ADMIN: Override requested for application id={}, reason={}", id, request.reason());

        if (request.reason() == null || request.reason().isBlank()) {
            return ResponseEntity.badRequest().body(new RestResponse<>(400, "Reason is required for admin override operations"));
        }

        ApplicationResponse application = adminApplicationService.overrideApplication(id, request);

        return ResponseEntity.ok(new RestResponse<>(200, "Application overridden successfully", application));
    }

    /**
     * 7. Get soft-deleted applications
     * GET /api/v1/applications/admin/deleted?page=0&size=20
     */
    @GetMapping("/deleted")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestResponse<Page<ApplicationResponse>>> getDeletedApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("ADMIN: Fetching deleted applications, page={}, size={}", page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<ApplicationResponse> deleted = adminApplicationService.getDeletedApplications(pageable);

        return ResponseEntity.ok(new RestResponse<>(200, "Deleted applications fetched successfully", deleted));
    }

    /**
     * 8. Restore soft-deleted application
     * PUT /api/v1/applications/admin/{id}/restore
     */
    @PutMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestResponse<ApplicationResponse>> restoreApplication(
            @PathVariable String id
    ) {
        log.warn("ADMIN: Restoring application id={}", id);
        ApplicationResponse application = adminApplicationService.restoreApplication(id);
        return ResponseEntity.ok(new RestResponse<>(200, "Application restored successfully", application));
    }
}
