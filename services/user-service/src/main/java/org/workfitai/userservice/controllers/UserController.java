package org.workfitai.userservice.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.workfitai.userservice.dto.response.ResponseData;
import org.workfitai.userservice.dto.response.UserBaseResponse;
import org.workfitai.userservice.services.UserService;

import java.util.UUID;

/**
 * Controller for user-related operations.
 * Provides endpoints for user profile and validation.
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Get current user profile based on user ID from JWT token.
     * Returns role-specific profile (CandidateResponse, HRResponse, or
     * AdminResponse).
     *
     * @param userId the user ID extracted from JWT token by API Gateway
     * @return user profile based on their role
     */
    @GetMapping("/me")
    public ResponseEntity<ResponseData<Object>> getCurrentUserProfile(
            @RequestHeader("X-User-Id") UUID userId) {
        Object profile = userService.getCurrentUserProfile(userId);
        return ResponseEntity.ok(ResponseData.success("Profile retrieved successfully", profile));
    }

    /**
     * Get user by email.
     *
     * @param email the user's email
     * @return user base response
     */
    @GetMapping("/by-email")
    public ResponseEntity<ResponseData<UserBaseResponse>> getByEmail(
            @RequestParam("email") String email) {
        UserBaseResponse user = userService.getByEmail(email);
        return ResponseEntity.ok(ResponseData.success("User retrieved successfully", user));
    }

    /**
     * Get user by username.
     *
     * @param username the user's username
     * @return user base response
     */
    @GetMapping("/by-username")
    public ResponseEntity<ResponseData<UserBaseResponse>> getByUsername(
            @RequestParam("username") String username) {
        UserBaseResponse user = userService.getByUsername(username);
        return ResponseEntity.ok(ResponseData.success("User retrieved successfully", user));
    }

    /**
     * Check if an email already exists in user-service.
     * Used by auth-service during registration to ensure data consistency.
     *
     * @param email the email to check
     * @return true if email exists, false otherwise
     */
    @GetMapping("/exists/email")
    public ResponseEntity<Boolean> existsByEmail(@RequestParam("email") String email) {
        boolean exists = userService.existsByEmail(email);
        return ResponseEntity.ok(exists);
    }

    /**
     * Check if a username already exists in user-service.
     * Used by auth-service during registration to ensure data consistency.
     *
     * @param username the username to check
     * @return true if username exists, false otherwise
     */
    @GetMapping("/exists/username")
    public ResponseEntity<Boolean> existsByUsername(@RequestParam("username") String username) {
        boolean exists = userService.existsByUsername(username);
        return ResponseEntity.ok(exists);
    }
}
