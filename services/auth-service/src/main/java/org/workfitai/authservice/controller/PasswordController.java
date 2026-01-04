package org.workfitai.authservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.workfitai.authservice.dto.request.ChangePasswordRequest;
import org.workfitai.authservice.dto.request.ForgotPasswordRequest;
import org.workfitai.authservice.dto.request.ResetPasswordRequest;
import org.workfitai.authservice.dto.request.SetPasswordRequest;
import org.workfitai.authservice.dto.request.VerifyOtpRequest;
import org.workfitai.authservice.dto.response.PasswordResetResponse;
import org.workfitai.authservice.service.PasswordService;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordService passwordService;

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("Password change request for user: {}", username);

        passwordService.changePassword(username, request);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Password changed successfully. You have been logged out from all devices");

        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<PasswordResetResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {

        log.info("Password reset request for email: {}", request.getEmail());

        PasswordResetResponse response = passwordService.forgotPassword(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-reset-otp")
    public ResponseEntity<Map<String, String>> verifyResetOtp(
            @Valid @RequestBody VerifyOtpRequest request) {

        log.info("Verifying reset OTP for email: {}", request.getEmail());

        Map<String, String> response = passwordService.verifyResetOtp(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        log.info("Password reset confirmation for token: {}", request.getToken());

        passwordService.resetPassword(request);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Password reset successfully. Please login with your new password");

        return ResponseEntity.ok(response);
    }

    /**
     * Set password for OAuth-only users
     * This allows users who registered via OAuth to enable traditional login
     */
    @PostMapping("/set-password")
    public ResponseEntity<Map<String, String>> setPassword(
            @Valid @RequestBody SetPasswordRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("Set password request for OAuth user: {}", username);

        passwordService.setPassword(username, request);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Password set successfully. You can now login with username and password");

        return ResponseEntity.ok(response);
    }
}
