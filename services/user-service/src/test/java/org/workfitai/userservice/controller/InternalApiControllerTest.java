package org.workfitai.userservice.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalApiControllerTest {

    @Mock NotificationSettingsService notificationSettingsService;
    @Mock HrNotificationSettingsService hrNotificationSettingsService;
    @Mock PrivacySettingsService privacySettingsService;
    @Mock UserServiceImpl userService;
    @Mock UserPlatformStatsService userPlatformStatsService;
    @InjectMocks InternalApiController controller;

    // ---- getNotificationSettingsByEmail ----

    @Test
    void getNotificationSettings_success_returnsSettings() {
        NotificationSettingsResponse settings = NotificationSettingsResponse.builder().build();
        when(notificationSettingsService.getNotificationSettingsByEmail("user@test.com"))
                .thenReturn(settings);

        ResponseEntity<NotificationSettingsResponse> resp =
                controller.getNotificationSettingsByEmail("user@test.com");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(settings);
    }

    @Test
    void getNotificationSettings_exception_returnsDefaultSettings() {
        when(notificationSettingsService.getNotificationSettingsByEmail("bad@test.com"))
                .thenThrow(new RuntimeException("service down"));

        ResponseEntity<NotificationSettingsResponse> resp =
                controller.getNotificationSettingsByEmail("bad@test.com");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getEmail().getJobAlerts()).isTrue();
    }

    // ---- getHrNotificationSettingsByEmail ----

    @Test
    void getHrNotificationSettings_success_returnsSettings() {
        HrNotificationSettingsResponse settings = HrNotificationSettingsResponse.builder()
                .notifyOnNewApplication(true).notifyOnJobExpiry(true).build();
        when(hrNotificationSettingsService.getHrNotificationSettingsByEmail("hr@test.com"))
                .thenReturn(settings);

        ResponseEntity<HrNotificationSettingsResponse> resp =
                controller.getHrNotificationSettingsByEmail("hr@test.com");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getNotifyOnNewApplication()).isTrue();
    }

    @Test
    void getHrNotificationSettings_exception_returnsDefaults() {
        when(hrNotificationSettingsService.getHrNotificationSettingsByEmail("bad@test.com"))
                .thenThrow(new RuntimeException("error"));

        ResponseEntity<HrNotificationSettingsResponse> resp =
                controller.getHrNotificationSettingsByEmail("bad@test.com");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getNotifyOnNewApplication()).isTrue();
    }

    // ---- getAiJobRecommendationSettings ----

    @Test
    void getAiConsent_enabled_returnsTrue() {
        when(privacySettingsService.getAiJobRecommendationConsentByUsername("alice")).thenReturn(true);

        ResponseEntity<AiJobRecommendationSettingsResponse> resp =
                controller.getAiJobRecommendationSettings("alice");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().isAiJobRecommendationEnabled()).isTrue();
    }

    @Test
    void getAiConsent_disabled_returnsFalse() {
        when(privacySettingsService.getAiJobRecommendationConsentByUsername("bob")).thenReturn(false);

        ResponseEntity<AiJobRecommendationSettingsResponse> resp =
                controller.getAiJobRecommendationSettings("bob");

        assertThat(resp.getBody().isAiJobRecommendationEnabled()).isFalse();
    }

    @Test
    void getAiConsent_exception_failsClosedReturnsFalse() {
        when(privacySettingsService.getAiJobRecommendationConsentByUsername("error"))
                .thenThrow(new RuntimeException("db error"));

        ResponseEntity<AiJobRecommendationSettingsResponse> resp =
                controller.getAiJobRecommendationSettings("error");

        assertThat(resp.getBody().isAiJobRecommendationEnabled()).isFalse();
    }

    // ---- getUsersByUsernames ----

    @Test
    void getUsersByUsernames_returnsUserList() {
        List<String> usernames = List.of("alice", "bob");
        List<UserInfoServeForJobResponse> users = List.of(
                UserInfoServeForJobResponse.builder().username("alice").build(),
                UserInfoServeForJobResponse.builder().username("bob").build()
        );
        when(userService.getUsersByUsernamesServeForJobUpdate(usernames)).thenReturn(users);

        ResponseEntity<List<UserInfoServeForJobResponse>> resp =
                controller.getUsersByUsernames(usernames);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(2);
    }

    // ---- getPlatformStats ----

    @Test
    void getPlatformStats_returnsWrappedStats() {
        when(userPlatformStatsService.getStats()).thenReturn(null);

        ResponseEntity<ResponseData<UserPlatformStatsResponse>> resp = controller.getPlatformStats();

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
