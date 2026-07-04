package org.workfitai.userservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.workfitai.userservice.dto.request.SettingsRequest;
import org.workfitai.userservice.dto.response.ResponseData;
import org.workfitai.userservice.dto.response.SettingsResponse;
import org.workfitai.userservice.service.SettingsService;

@Slf4j
@RestController
@RequestMapping("/profile/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public ResponseEntity<ResponseData<SettingsResponse>> getSettings(Authentication authentication) {
        String username = authentication.getName();
        log.info("Get settings request for user: {}", username);
        return ResponseEntity.ok(ResponseData.success(settingsService.getSettings(username)));
    }

    @PutMapping
    public ResponseEntity<ResponseData<SettingsResponse>> updateSettings(
            @Valid @RequestBody SettingsRequest request,
            Authentication authentication) {
        String username = authentication.getName();
        log.info("Update settings request for user: {}", username);
        return ResponseEntity.ok(ResponseData.success(settingsService.updateSettings(username, request)));
    }
}
