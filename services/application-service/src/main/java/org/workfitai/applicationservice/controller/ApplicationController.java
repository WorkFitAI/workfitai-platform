package org.workfitai.applicationservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.dto.response.ApiError;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.security.ApplicationSecurity;
import org.workfitai.applicationservice.service.IApplicationService;

import java.util.Map;

/**
 * REST Controller for job application operations.
 * 
 * Endpoints:
 * - POST /api/v1/applications - Create new application (Candidate)
 * - GET /api/v1/applications/my - Get my applications (Candidate)
 * - GET /api/v1/applications/{id} - Get single application
 * - GET /api/v1/applications/job/{jobId} - Get applications for a job (HR)
 * - PUT /api/v1/applications/{id}/status - Update status (HR)
 * - DELETE /api/v1/applications/{id} - Withdraw application (Candidate)
 * - GET /api/v1/applications/check - Check if already applied (Public)
 * 
 * Security:
 * - All endpoints except /check require JWT authentication
 * - Role-based access via @PreAuthorize
 * - User ID extracted from JWT "sub" claim
 * 
 * Response Format:
 * - Success: RestResponse<T> with status 200/201
 * - Error: ApiError with appropriate status code
 */
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Applications", description = "Job application management endpoints")
@SecurityRequirement(name = "bearerAuth") // All endpoints require auth by default
public class ApplicationController {

    private final IApplicationService applicationService;
    private final ApplicationSecurity applicationSecurity;

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 CREATE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Submit a new job application.
     * 
     * Authorization: Any authenticated user can apply
     * 
     * Business Rules:
     * - User can only apply once per job (409 Conflict if duplicate)
     * - Job must be PUBLISHED (403 Forbidden if not)
     * - CV must belong to the user (403 Forbidden if not)
     * 
     * @param request        Application data (jobId, cvId, note)
     * @param authentication JWT authentication (provides userId)
     * @return Created application with enriched data
     */
    @PostMapping
    @Operation(summary = "Submit a job application", description = "Apply to a job with your CV. Requires job to be PUBLISHED and CV to belong to you.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Application created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "403", description = "Job not published or CV not owned", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "404", description = "Job or CV not found", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Already applied to this job", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<RestResponse<ApplicationResponse>> createApplication(
            @Valid @RequestBody CreateApplicationRequest request,
            Authentication authentication) {

        // Extract userId from JWT token (not from request body for security)
        String userId = applicationSecurity.getCurrentUserId(authentication);
        log.info("User {} applying to job {}", userId, request.getJobId());

        ApplicationResponse response = applicationService.createApplication(request, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(RestResponse.created(response));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 READ OPERATIONS - CANDIDATE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get current user's applications (paginated).
     * 
     * Authorization: CANDIDATE role only
     * 
     * @param status         Optional filter by status
     * @param page           Page number (0-indexed)
     * @param size           Page size (default 10, max 100)
     * @param authentication JWT authentication
     * @return Paginated list of user's applications
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Get my applications", description = "Retrieve your job applications with optional status filter")
    public ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> getMyApplications(
            @RequestParam(required = false) @Parameter(description = "Filter by status") ApplicationStatus status,
            @RequestParam(defaultValue = "0") @Parameter(description = "Page number (0-indexed)") int page,
            @RequestParam(defaultValue = "10") @Parameter(description = "Page size (max 100)") int size,
            Authentication authentication) {

        String userId = applicationSecurity.getCurrentUserId(authentication);

        // Limit page size to prevent large queries
        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        ResultPaginationDTO<ApplicationResponse> result;
        if (status != null) {
            result = applicationService.getMyApplicationsByStatus(userId, status, pageable);
        } else {
            result = applicationService.getMyApplications(userId, pageable);
        }

        return ResponseEntity.ok(RestResponse.success(result));
    }

    /**
     * Get single application by ID.
     * 
     * Authorization:
     * - Owner can view their own application
     * - HR/ADMIN can view any application
     * 
     * @param id             Application ID
     * @param authentication JWT authentication
     * @return Application details
     */
    @GetMapping("/{id}")
    @PreAuthorize("@applicationSecurity.canView(#id, authentication)")
    @Operation(summary = "Get application by ID", description = "Retrieve application details. Requires ownership or HR/ADMIN role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Application found"),
            @ApiResponse(responseCode = "403", description = "Not authorized to view"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<RestResponse<ApplicationResponse>> getApplication(
            @PathVariable String id,
            Authentication authentication) {

        ApplicationResponse response = applicationService.getApplicationById(id);
        return ResponseEntity.ok(RestResponse.success(response));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 READ OPERATIONS - HR
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get applications for a specific job (paginated).
     * 
     * Authorization: HR or ADMIN role
     * 
     * Use case: HR viewing applicants for their job posting
     * 
     * @param jobId  Job ID to get applicants for
     * @param status Optional filter by status
     * @param page   Page number
     * @param size   Page size
     * @return Paginated list of applications
     */
    @GetMapping("/job/{jobId}")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    @Operation(summary = "Get applications for a job", description = "Retrieve all applications for a specific job. HR/ADMIN only.")
    public ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> getApplicationsByJob(
            @PathVariable String jobId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        ResultPaginationDTO<ApplicationResponse> result;
        if (status != null) {
            result = applicationService.getApplicationsByJobAndStatus(jobId, status, pageable);
        } else {
            result = applicationService.getApplicationsByJob(jobId, pageable);
        }

        return ResponseEntity.ok(RestResponse.success(result));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 UPDATE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Update application status.
     * 
     * Authorization: HR or ADMIN role
     * 
     * Status Flow:
     * APPLIED → REVIEWING → INTERVIEW → OFFER → HIRED
     * ↘ REJECTED
     * 
     * @param id     Application ID
     * @param status New status
     * @return Updated application
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("@applicationSecurity.canUpdateStatus(#id, authentication)")
    @Operation(summary = "Update application status", description = "Change application status (e.g., REVIEWING, INTERVIEW). HR/ADMIN only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated"),
            @ApiResponse(responseCode = "400", description = "Invalid status transition"),
            @ApiResponse(responseCode = "403", description = "Not authorized"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<RestResponse<ApplicationResponse>> updateStatus(
            @PathVariable String id,
            @RequestParam ApplicationStatus status,
            Authentication authentication) {

        log.info("Updating application {} status to {}", id, status);
        ApplicationResponse response = applicationService.updateStatus(id, status);

        return ResponseEntity.ok(RestResponse.success(response));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 DELETE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Withdraw an application.
     * 
     * Authorization: Only the applicant can withdraw
     * 
     * @param id             Application ID
     * @param authentication JWT authentication
     * @return No content on success
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@applicationSecurity.isOwner(#id, authentication)")
    @Operation(summary = "Withdraw application", description = "Cancel your job application. Only the applicant can withdraw.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Application withdrawn"),
            @ApiResponse(responseCode = "403", description = "Not your application"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    public ResponseEntity<Void> withdrawApplication(
            @PathVariable String id,
            Authentication authentication) {

        String userId = applicationSecurity.getCurrentUserId(authentication);
        log.info("User {} withdrawing application {}", userId, id);

        applicationService.withdrawApplication(id, userId);
        return ResponseEntity.noContent().build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 PUBLIC ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Check if user has already applied to a job.
     * 
     * Authorization: Authenticated users only
     * 
     * Use case: Frontend checks before showing "Apply" button
     * 
     * @param jobId          Job ID to check
     * @param authentication JWT authentication
     * @return { "applied": true/false }
     */
    @GetMapping("/check")
    @Operation(summary = "Check if already applied", description = "Check if current user has already applied to a specific job")
    public ResponseEntity<RestResponse<Map<String, Boolean>>> hasApplied(
            @RequestParam String jobId,
            Authentication authentication) {

        String userId = applicationSecurity.getCurrentUserId(authentication);
        boolean hasApplied = applicationService.hasUserAppliedToJob(userId, jobId);

        return ResponseEntity.ok(RestResponse.success(Map.of("applied", hasApplied)));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 🔸 STATISTICS ENDPOINTS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Get application count for current user.
     * 
     * @param authentication JWT authentication
     * @return { "count": N }
     */
    @GetMapping("/my/count")
    @PreAuthorize("hasRole('CANDIDATE')")
    @Operation(summary = "Get my application count")
    public ResponseEntity<RestResponse<Map<String, Long>>> getMyApplicationCount(
            Authentication authentication) {

        String userId = applicationSecurity.getCurrentUserId(authentication);
        long count = applicationService.countByUser(userId);

        return ResponseEntity.ok(RestResponse.success(Map.of("count", count)));
    }

    /**
     * Get applicant count for a job.
     * 
     * @param jobId Job ID
     * @return { "count": N }
     */
    @GetMapping("/job/{jobId}/count")
    @PreAuthorize("hasAnyRole('HR', 'ADMIN')")
    @Operation(summary = "Get applicant count for a job")
    public ResponseEntity<RestResponse<Map<String, Long>>> getJobApplicationCount(
            @PathVariable String jobId) {

        long count = applicationService.countByJob(jobId);
        return ResponseEntity.ok(RestResponse.success(Map.of("count", count)));
    }
}
