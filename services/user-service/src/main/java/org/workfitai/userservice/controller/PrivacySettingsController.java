package org.workfitai.userservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.workfitai.userservice.dto.request.PrivacySettingsRequest;
import org.workfitai.userservice.dto.response.PrivacySettingsResponse;
import org.workfitai.userservice.service.PrivacySettingsService;

@Slf4j
@RestController
@RequestMapping("/profile/privacy-settings")
@RequiredArgsConstructor
public class PrivacySettingsController {

    private final PrivacySettingsService privacySettingsService;

    @GetMapping
    public ResponseEntity<PrivacySettingsResponse> getPrivacySettings(Authentication authentication) {
        String username = authentication.getName();
        log.info("Get privacy settings request for user: {}", username);

        PrivacySettingsResponse settings = privacySettingsService.getPrivacySettings(username);

        return ResponseEntity.ok(settings);
    }

    @PutMapping
    public ResponseEntity<PrivacySettingsResponse> updatePrivacySettings(
            @Valid @RequestBody PrivacySettingsRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("Update privacy settings request for user: {}", username);

        PrivacySettingsResponse settings = privacySettingsService.updatePrivacySettings(username, request);

        return ResponseEntity.ok(settings);
    }
}
