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
import org.workfitai.authservice.dto.response.PasswordResetResponse;
import org.workfitai.authservice.service.PasswordService;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
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

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {

        log.info("Password reset confirmation for token: {}", request.getToken());

        passwordService.resetPassword(request);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Password reset successfully. Please login with your new password");

        return ResponseEntity.ok(response);
    }
}
