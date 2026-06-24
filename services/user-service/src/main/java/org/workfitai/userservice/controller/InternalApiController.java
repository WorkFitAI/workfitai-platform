package org.workfitai.userservice.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.workfitai.userservice.dto.response.AiJobRecommendationSettingsResponse;
import org.workfitai.userservice.dto.response.HrNotificationSettingsResponse;
import org.workfitai.userservice.dto.response.NotificationSettingsResponse;
import org.workfitai.userservice.dto.response.ResponseData;
import org.workfitai.userservice.dto.response.UserInfoServeForJobResponse;
import org.workfitai.userservice.dto.response.UserPlatformStatsResponse;
import org.workfitai.userservice.service.HrNotificationSettingsService;
import org.workfitai.userservice.service.NotificationSettingsService;
import org.workfitai.userservice.service.PrivacySettingsService;
import org.workfitai.userservice.service.UserPlatformStatsService;
import org.workfitai.userservice.service.impl.UserServiceImpl;

/**
 * Internal API endpoints for inter-service communication.
 * NOT exposed through API Gateway - only accessible within the cluster.
 */
@RestController
@RequestMapping("/api/v1/internal")
@RequiredArgsConstructor
@Slf4j
public class InternalApiController {

        private final NotificationSettingsService notificationSettingsService;

        private final HrNotificationSettingsService hrNotificationSettingsService;

        private final PrivacySettingsService privacySettingsService;

        private final UserServiceImpl userService;

        private final UserPlatformStatsService userPlatformStatsService;

        /**
         * Get notification settings by email (for notification-service)
         */
        @GetMapping("/notification-settings/{email}")
        public ResponseEntity<NotificationSettingsResponse> getNotificationSettingsByEmail(
                        @PathVariable String email) {
                log.debug("Internal API: Getting notification settings for email: {}", email);

                try {
                        NotificationSettingsResponse settings = notificationSettingsService
                                        .getNotificationSettingsByEmail(email);
                        return ResponseEntity.ok(settings);
                } catch (Exception e) {
                        log.warn("Failed to get notification settings for email {}: {}", email, e.getMessage());
                        // Return default settings (all enabled) on error
                        return ResponseEntity.ok(NotificationSettingsResponse.builder()
                                        .email(NotificationSettingsResponse.EmailNotifications.builder()
                                                        .jobAlerts(true)
                                                        .applicationUpdates(true)
                                                        .messages(true)
                                                        .newsletter(false)
                                                        .marketingEmails(false)
                                                        .securityAlerts(true)
                                                        .build())
                                        .push(NotificationSettingsResponse.PushNotifications.builder()
                                                        .jobAlerts(true)
                                                        .applicationUpdates(true)
                                                        .messages(true)
                                                        .reminders(true)
                                                        .build())
                                        .build());
                }
        }

        /**
         * Get HR notification settings by email (for application-service / job-service).
         * Fails open (defaults enabled) on any error, matching
         * getNotificationSettingsByEmail()'s resilience characteristics.
         */
        @GetMapping("/hr-notification-settings/{email}")
        public ResponseEntity<HrNotificationSettingsResponse> getHrNotificationSettingsByEmail(
                        @PathVariable String email) {
                log.debug("Internal API: Getting HR notification settings for email: {}", email);

                try {
                        HrNotificationSettingsResponse settings = hrNotificationSettingsService
                                        .getHrNotificationSettingsByEmail(email);
                        return ResponseEntity.ok(settings);
                } catch (Exception e) {
                        log.warn("Failed to get HR notification settings for email {}: {}", email, e.getMessage());
                        return ResponseEntity.ok(HrNotificationSettingsResponse.builder()
                                        .notifyOnNewApplication(true)
                                        .notifyOnJobExpiry(true)
                                        .build());
                }
        }

        /**
         * Get a candidate's AI job-recommendation consent by username (for
         * recommendation-engine). Fails CLOSED (treats consent as NOT granted)
         * on any error - this is a consent gate for AI processing of personal
         * data, default is opt-in, so an inability to confirm consent must not
         * be silently treated as consent. Unlike the fail-open precedent used
         * for non-sensitive notification-preference lookups elsewhere.
         */
        @GetMapping("/ai-job-recommendation-settings/{username}")
        public ResponseEntity<AiJobRecommendationSettingsResponse> getAiJobRecommendationSettings(
                        @PathVariable String username) {
                log.debug("Internal API: Getting AI job-recommendation consent for username: {}", username);

                try {
                        boolean enabled = privacySettingsService.getAiJobRecommendationConsentByUsername(username);
                        return ResponseEntity.ok(AiJobRecommendationSettingsResponse.builder()
                                        .aiJobRecommendationEnabled(enabled)
                                        .build());
                } catch (Exception e) {
                        log.warn("Failed to get AI job-recommendation consent for {}: {} - failing closed", username,
                                        e.getMessage());
                        return ResponseEntity.ok(AiJobRecommendationSettingsResponse.builder()
                                        .aiJobRecommendationEnabled(false)
                                        .build());
                }
        }

        @GetMapping("/users")
        public ResponseEntity<List<UserInfoServeForJobResponse>> getUsersByUsernames(
                        @RequestParam List<String> usernames) {
                List<UserInfoServeForJobResponse> users = userService.getUsersByUsernamesServeForJobUpdate(usernames);
                return ResponseEntity.ok(users);
        }

        @GetMapping("/admin/stats")
        public ResponseEntity<ResponseData<UserPlatformStatsResponse>> getPlatformStats() {
                return ResponseEntity.ok(ResponseData.success(userPlatformStatsService.getStats()));
        }

}
