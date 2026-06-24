package org.workfitai.userservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.workfitai.userservice.dto.request.HrNotificationSettingsRequest;
import org.workfitai.userservice.dto.response.HrNotificationSettingsResponse;
import org.workfitai.userservice.service.HrNotificationSettingsService;

@Slf4j
@RestController
@RequestMapping("/profile/hr-notification-settings")
@RequiredArgsConstructor
public class HrNotificationSettingsController {

    private final HrNotificationSettingsService hrNotificationSettingsService;

    @GetMapping
    @PreAuthorize("hasAuthority('hr:read') and hasAnyRole('HR', 'HR_MANAGER')")
    public ResponseEntity<HrNotificationSettingsResponse> getHrNotificationSettings(Authentication authentication) {
        String username = authentication.getName();
        log.info("Get HR notification settings request for user: {}", username);

        HrNotificationSettingsResponse settings = hrNotificationSettingsService.getHrNotificationSettings(username);

        return ResponseEntity.ok(settings);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('hr:update') and hasAnyRole('HR', 'HR_MANAGER')")
    public ResponseEntity<HrNotificationSettingsResponse> updateHrNotificationSettings(
            @Valid @RequestBody HrNotificationSettingsRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("Update HR notification settings request for user: {}", username);

        HrNotificationSettingsResponse settings = hrNotificationSettingsService
                .updateHrNotificationSettings(username, request);

        return ResponseEntity.ok(settings);
    }
}
