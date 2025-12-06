package org.workfitai.userservice.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.workfitai.userservice.constants.Messages;
import org.workfitai.userservice.dto.request.AdminUpdateRequest;
import org.workfitai.userservice.dto.request.CandidateUpdateRequest;
import org.workfitai.userservice.dto.request.HRUpdateRequest;
import org.workfitai.userservice.dto.response.*;
import org.workfitai.userservice.services.AdminService;
import org.workfitai.userservice.services.CandidateService;
import org.workfitai.userservice.services.HRService;
import org.workfitai.userservice.services.UserService;

import java.util.UUID;

/**
 * Controller for user profile operations.
 * Allows authenticated users to view and update their own profiles.
 */
@RestController
@RequestMapping("/profile")
@RequiredArgsConstructor
@Slf4j
public class UserProfileController {

    private final UserService userService;
    private final CandidateService candidateService;
    private final HRService hrService;
    private final AdminService adminService;

    /**
     * Get current user's profile.
     * Returns role-specific profile (CandidateResponse, HRResponse, or
     * AdminResponse).
     *
     * @param userId the user ID extracted from JWT token by API Gateway
     * @return user profile based on their role
     */
    @GetMapping("/me")
    public ResponseEntity<ResponseData<Object>> getMyProfile(
            @RequestHeader("X-User-Id") UUID userId) {
        log.info("Getting profile for user: {}", userId);
        Object profile = userService.getCurrentUserProfile(userId);
        return ResponseEntity.ok(ResponseData.success(Messages.User.PROFILE_FETCHED, profile));
    }

    /**
     * Update candidate profile.
     *
     * @param userId  the user ID extracted from JWT token by API Gateway
     * @param request the update request
     * @return updated candidate profile
     */
    @PutMapping("/candidate")
    public ResponseEntity<ResponseData<CandidateResponse>> updateCandidateProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CandidateUpdateRequest request) {
        log.info("Updating candidate profile for user: {}", userId);
        CandidateResponse updated = candidateService.update(userId, request);
        return ResponseEntity.ok(ResponseData.success(Messages.Profile.CANDIDATE_PROFILE_UPDATED, updated));
    }

    /**
     * Update HR profile.
     *
     * @param userId  the user ID extracted from JWT token by API Gateway
     * @param request the update request
     * @return updated HR profile
     */
    @PutMapping("/hr")
    public ResponseEntity<ResponseData<HRResponse>> updateHRProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody HRUpdateRequest request) {
        log.info("Updating HR profile for user: {}", userId);
        HRResponse updated = hrService.update(userId, request);
        return ResponseEntity.ok(ResponseData.success(Messages.Profile.HR_PROFILE_UPDATED, updated));
    }

    /**
     * Update admin profile.
     *
     * @param userId  the user ID extracted from JWT token by API Gateway
     * @param request the update request
     * @return updated admin profile
     */
    @PutMapping("/admin")
    public ResponseEntity<ResponseData<AdminResponse>> updateAdminProfile(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody AdminUpdateRequest request) {
        log.info("Updating admin profile for user: {}", userId);
        AdminResponse updated = adminService.update(userId, request);
        return ResponseEntity.ok(ResponseData.success(Messages.Profile.ADMIN_PROFILE_UPDATED, updated));
    }
}
