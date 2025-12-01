package org.workfitai.applicationservice.controller;

import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.applicationservice.constants.Messages;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.dto.response.ApiError;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.RestResponse;
import org.workfitai.applicationservice.dto.response.ResultPaginationDTO;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.security.ApplicationSecurity;
import org.workfitai.applicationservice.service.IApplicationService;

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

/**
 * REST Controller for job application operations.
 * All state changes publish events to Kafka for cross-service communication.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
@Tag(name = Messages.Api.TAG_NAME, description = Messages.Api.TAG_DESCRIPTION)
@SecurityRequirement(name = "bearerAuth")
public class ApplicationController {

    private final IApplicationService applicationService;
    private final ApplicationSecurity applicationSecurity;

    @PostMapping
    @PreAuthorize("hasAuthority('application:create')")
    @Operation(summary = Messages.Api.CREATE_SUMMARY, description = Messages.Api.CREATE_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = Messages.Api.CREATE_201),
            @ApiResponse(responseCode = "400", description = Messages.Api.CREATE_400, content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = Messages.Api.CREATE_409, content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<RestResponse<ApplicationResponse>> createApplication(
            @Valid @RequestBody CreateApplicationRequest request,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        log.info(Messages.Log.CREATING_APPLICATION, username, request.getJobId());

        ApplicationResponse response = applicationService.createApplication(request, username);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(RestResponse.created(response));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAuthority('application:list')")
    @Operation(summary = Messages.Api.GET_MY_SUMMARY, description = Messages.Api.GET_MY_DESCRIPTION)
    public ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> getMyApplications(
            @RequestParam(required = false) @Parameter(description = Messages.Api.PARAM_STATUS) ApplicationStatus status,
            @RequestParam(defaultValue = "0") @Parameter(description = Messages.Api.PARAM_PAGE) int page,
            @RequestParam(defaultValue = "10") @Parameter(description = Messages.Api.PARAM_SIZE) int size,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        ResultPaginationDTO<ApplicationResponse> result;
        if (status != null) {
            result = applicationService.getMyApplicationsByStatus(username, status, pageable);
        } else {
            result = applicationService.getMyApplications(username, pageable);
        }

        return ResponseEntity.ok(RestResponse.success(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@applicationSecurity.canView(#id, authentication)")
    @Operation(summary = Messages.Api.GET_BY_ID_SUMMARY, description = Messages.Api.GET_BY_ID_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = Messages.Api.GET_200),
            @ApiResponse(responseCode = "403", description = Messages.Api.GET_403),
            @ApiResponse(responseCode = "404", description = Messages.Api.GET_404)
    })
    public ResponseEntity<RestResponse<ApplicationResponse>> getApplication(
            @PathVariable String id,
            Authentication authentication) {

        ApplicationResponse response = applicationService.getApplicationById(id);
        return ResponseEntity.ok(RestResponse.success(response));
    }

    @GetMapping("/job/{jobId}")
    @PreAuthorize("hasAuthority('application:review')")
    @Operation(summary = Messages.Api.GET_BY_JOB_SUMMARY, description = Messages.Api.GET_BY_JOB_DESCRIPTION)
    public ResponseEntity<RestResponse<ResultPaginationDTO<ApplicationResponse>>> getApplicationsByJob(
            @PathVariable @Parameter(description = Messages.Api.PARAM_JOB_ID) String jobId,
            @RequestParam(required = false) @Parameter(description = Messages.Api.PARAM_STATUS) ApplicationStatus status,
            @RequestParam(defaultValue = "0") @Parameter(description = Messages.Api.PARAM_PAGE) int page,
            @RequestParam(defaultValue = "10") @Parameter(description = Messages.Api.PARAM_SIZE) int size) {

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

    @PutMapping("/{id}/status")
    @PreAuthorize("@applicationSecurity.canUpdateStatus(#id, authentication)")
    @Operation(summary = Messages.Api.UPDATE_STATUS_SUMMARY, description = Messages.Api.UPDATE_STATUS_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = Messages.Api.UPDATE_200),
            @ApiResponse(responseCode = "400", description = Messages.Api.UPDATE_400),
            @ApiResponse(responseCode = "403", description = Messages.Api.GET_403),
            @ApiResponse(responseCode = "404", description = Messages.Api.GET_404)
    })
    public ResponseEntity<RestResponse<ApplicationResponse>> updateStatus(
            @PathVariable String id,
            @RequestParam ApplicationStatus status,
            Authentication authentication) {

        String updatedBy = applicationSecurity.getCurrentUsername(authentication);
        log.info(Messages.Log.UPDATING_STATUS, id, status);

        ApplicationResponse response = applicationService.updateStatus(id, status, updatedBy);
        return ResponseEntity.ok(RestResponse.success(response));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@applicationSecurity.isOwner(#id, authentication)")
    @Operation(summary = Messages.Api.WITHDRAW_SUMMARY, description = Messages.Api.WITHDRAW_DESCRIPTION)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = Messages.Api.WITHDRAW_204),
            @ApiResponse(responseCode = "403", description = Messages.Api.WITHDRAW_403),
            @ApiResponse(responseCode = "404", description = Messages.Api.GET_404)
    })
    public ResponseEntity<Void> withdrawApplication(
            @PathVariable String id,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        log.info(Messages.Log.WITHDRAWING_APPLICATION, username, id);

        applicationService.withdrawApplication(id, username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/check")
    @PreAuthorize("hasAuthority('application:read')")
    @Operation(summary = Messages.Api.CHECK_SUMMARY, description = Messages.Api.CHECK_DESCRIPTION)
    public ResponseEntity<RestResponse<Map<String, Boolean>>> hasApplied(
            @RequestParam @Parameter(description = Messages.Api.PARAM_JOB_ID) String jobId,
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        boolean hasApplied = applicationService.hasUserAppliedToJob(username, jobId);

        return ResponseEntity.ok(RestResponse.success(Map.of("applied", hasApplied)));
    }

    @GetMapping("/my/count")
    @PreAuthorize("hasAuthority('application:list')")
    @Operation(summary = Messages.Api.COUNT_MY_SUMMARY)
    public ResponseEntity<RestResponse<Map<String, Long>>> getMyApplicationCount(
            Authentication authentication) {

        String username = applicationSecurity.getCurrentUsername(authentication);
        long count = applicationService.countByUser(username);

        return ResponseEntity.ok(RestResponse.success(Map.of("count", count)));
    }

    @GetMapping("/job/{jobId}/count")
    @PreAuthorize("hasAuthority('application:review')")
    @Operation(summary = Messages.Api.COUNT_JOB_SUMMARY)
    public ResponseEntity<RestResponse<Map<String, Long>>> getJobApplicationCount(
            @PathVariable @Parameter(description = Messages.Api.PARAM_JOB_ID) String jobId) {

        long count = applicationService.countByJob(jobId);
        return ResponseEntity.ok(RestResponse.success(Map.of("count", count)));
    }
}
