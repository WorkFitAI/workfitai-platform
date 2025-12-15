package org.workfitai.applicationservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.workfitai.applicationservice.dto.request.AdminCreateApplicationRequest;
import org.workfitai.applicationservice.dto.request.AdminOverrideRequest;
import org.workfitai.applicationservice.dto.response.*;
import org.workfitai.applicationservice.model.AuditLog;
import org.workfitai.applicationservice.service.*;

import java.time.Instant;
import java.util.List;

/**
 * Admin-only controller for system-level operations
 * All endpoints require ROLE_ADMIN
 */
@RestController
@RequestMapping("/api/v1/applications/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final AdminApplicationService adminApplicationService;
    private final SystemStatsService systemStatsService;
    private final AuditLogService auditLogService;
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

        return ResponseEntity.ok(
                new RestResponse<>(
                        200,
                        "All applications fetched successfully",
                        applications
                )
        );
    }

    /**
     * 2. Get audit logs
     * GET /api/v1/applications/admin/audit?entityId={id}&performedBy={user}&action={action}&fromDate={date}&page=0
     */
    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestResponse<Page<AuditLogResponse>>> getAuditLogs(
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String performedBy,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Instant fromDate,
            @RequestParam(required = false) Instant toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("ADMIN: Querying audit logs, entityId={}, performedBy={}, action={}",
                entityId, performedBy, action);

        // Validate date range
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            return ResponseEntity.badRequest().body(
                    new RestResponse<>(
                            400,
                            "fromDate must be before toDate"
                    )
            );
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> auditLogs = auditLogService.queryAuditLogs(
                entityId, performedBy, action, fromDate, toDate, pageable
        );

        // Map to response DTOs
        Page<AuditLogResponse> response = auditLogs.map(log ->
                new AuditLogResponse(
                        log.getId(),
                        log.getEntityType(),
                        log.getEntityId(),
                        log.getAction(),
                        log.getPerformedBy(),
                        log.getPerformedAt(),
                        log.getBeforeState(),
                        log.getAfterState(),
                        log.getMetadata(),
                        log.getContainsPII()
                )
        );

        return ResponseEntity.ok(
                new RestResponse<>(
                        200,
                        "Audit logs fetched successfully",
                        response
                )
        );
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
            @RequestParam(required = false) Instant fromDate,
            @RequestParam(required = false) Instant toDate,
            @RequestBody(required = false) List<String> columns
    ) {
        // Get current user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "unknown";

        // Check rate limit: 5 exports per 24 hours
        if (!rateLimitService.isAllowed("admin-export", username, 5, 24)) {
            int remaining = rateLimitService.getRemainingRequests("admin-export", username, 5, 24);
            long resetSeconds = rateLimitService.getResetTimeSeconds("admin-export", username, 24);
            long resetHours = resetSeconds / 3600;

            log.warn("ADMIN: Export rate limit exceeded for user={}, remaining={}, resetIn={}h",
                username, remaining, resetHours);

            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    new RestResponse<>(
                            429,
                            String.format("Export rate limit exceeded. Limit: 5 per day. Reset in %d hours.", resetHours)
                    )
            );
        }

        log.warn("ADMIN: Full platform export requested by user={}, includeDeleted={}", username, includeDeleted);

        // Validate date range
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            return ResponseEntity.badRequest().body(
                    new RestResponse<>(
                            400,
                            "fromDate must be before toDate"
                    )
            );
        }

        ExportResponse export = exportService.exportAllApplications(
                includeDeleted, fromDate, toDate, columns
        );

        return ResponseEntity.ok(
                new RestResponse<>(
                        200,
                        "Export generated successfully",
                        export
                )
        );
    }

    /**
     * 4. System statistics
     * GET /api/v1/applications/admin/stats
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestResponse<SystemStatsResponse>> getSystemStats() {
        log.info("ADMIN: Fetching system statistics");

        SystemStatsResponse stats = systemStatsService.getSystemStats();

        return ResponseEntity.ok(
                new RestResponse<>(
                        200,
                        "System statistics fetched successfully",
                        stats
                )
        );
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
        log.warn("ADMIN: Manual application creation requested for user={}, job={}",
                request.username(), request.jobId());

        if (request.reason() == null || request.reason().isBlank()) {
            return ResponseEntity.badRequest().body(
                    new RestResponse<>(
                            400,
                            "Reason is required for admin manual creation"
                    )
            );
        }

        ApplicationResponse application = adminApplicationService.createApplication(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new RestResponse<>(
                        201,
                        "Application created successfully (manual creation)",
                        application
                )
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
        log.warn("ADMIN: Override requested for application id={}, reason={}",
                id, request.reason());

        if (request.reason() == null || request.reason().isBlank()) {
            return ResponseEntity.badRequest().body(
                    new RestResponse<>(
                            400,
                            "Reason is required for admin override operations"
                    )
            );
        }

        ApplicationResponse application = adminApplicationService.overrideApplication(id, request);

        return ResponseEntity.ok(
                new RestResponse<>(
                        200,
                        "Application overridden successfully",
                        application
                )
        );
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

        return ResponseEntity.ok(
                new RestResponse<>(
                        200,
                        "Deleted applications fetched successfully",
                        deleted
                )
        );
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

        return ResponseEntity.ok(
                new RestResponse<>(
                        200,
                        "Application restored successfully",
                        application
                )
        );
    }
}
