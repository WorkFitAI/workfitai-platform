package org.workfitai.authservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.workfitai.authservice.dto.request.Disable2FARequest;
import org.workfitai.authservice.dto.request.Enable2FARequest;
import org.workfitai.authservice.dto.response.Enable2FAResponse;
import org.workfitai.authservice.service.TwoFactorAuthService;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class TwoFactorAuthController {

    private final TwoFactorAuthService twoFactorAuthService;

    @PostMapping("/enable-2fa")
    public ResponseEntity<Enable2FAResponse> enable2FA(
            @Valid @RequestBody Enable2FARequest request,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("Enable 2FA request for user: {} with method: {}", username, request.getMethod());

        Enable2FAResponse response = twoFactorAuthService.enable2FA(username, request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/disable-2fa")
    public ResponseEntity<Map<String, String>> disable2FA(
            @Valid @RequestBody Disable2FARequest request,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("Disable 2FA request for user: {}", username);

        Map<String, String> response = twoFactorAuthService.disable2FA(username, request);

        return ResponseEntity.ok(response);
    }
}
