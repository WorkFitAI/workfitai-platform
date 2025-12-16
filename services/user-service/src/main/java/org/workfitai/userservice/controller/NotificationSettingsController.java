package org.workfitai.userservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.workfitai.userservice.dto.request.NotificationSettingsRequest;
import org.workfitai.userservice.dto.response.NotificationSettingsResponse;
import org.workfitai.userservice.service.NotificationSettingsService;

@Slf4j
@RestController
@RequestMapping("/profile/notification-settings")
@RequiredArgsConstructor
public class NotificationSettingsController {

    private final NotificationSettingsService notificationSettingsService;

    @GetMapping
    public ResponseEntity<NotificationSettingsResponse> getNotificationSettings(Authentication authentication) {
        String username = authentication.getName();
        log.info("Get notification settings request for user: {}", username);

        NotificationSettingsResponse settings = notificationSettingsService.getNotificationSettings(username);

        return ResponseEntity.ok(settings);
    }

    @PutMapping
    public ResponseEntity<NotificationSettingsResponse> updateNotificationSettings(
            @RequestBody NotificationSettingsRequest request,
            Authentication authentication) {

        String username = authentication.getName();
        log.info("Update notification settings request for user: {}", username);

        NotificationSettingsResponse settings = notificationSettingsService.updateNotificationSettings(username,
                request);

        return ResponseEntity.ok(settings);
    }
}
