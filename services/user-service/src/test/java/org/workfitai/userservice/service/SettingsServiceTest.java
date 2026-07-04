package org.workfitai.userservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.workfitai.userservice.dto.request.HrNotificationSettingsRequest;
import org.workfitai.userservice.dto.request.NotificationSettingsRequest;
import org.workfitai.userservice.dto.request.PrivacySettingsRequest;
import org.workfitai.userservice.dto.request.SettingsRequest;
import org.workfitai.userservice.dto.response.FeatureToggleResponse;
import org.workfitai.userservice.dto.response.HrNotificationSettingsResponse;
import org.workfitai.userservice.dto.response.NotificationSettingsResponse;
import org.workfitai.userservice.dto.response.PrivacySettingsResponse;
import org.workfitai.userservice.dto.response.SettingsResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.exception.NotFoundException;
import org.workfitai.userservice.model.UserEntity;
import org.workfitai.userservice.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SettingsServiceTest {

    @Mock UserRepository userRepository;
    @Mock NotificationSettingsService notificationSettingsService;
    @Mock PrivacySettingsService privacySettingsService;
    @Mock HrNotificationSettingsService hrNotificationSettingsService;
    @Mock PlatformFeatureToggleService platformFeatureToggleService;
    @InjectMocks SettingsService service;

    private static final String USERNAME = "testuser";
    private final NotificationSettingsResponse notifResp = NotificationSettingsResponse.builder().build();
    private final PrivacySettingsResponse privacyResp = PrivacySettingsResponse.builder().build();
    private final HrNotificationSettingsResponse hrNotifResp = HrNotificationSettingsResponse.builder().build();
    private final List<FeatureToggleResponse> featureList = List.of(
            FeatureToggleResponse.builder().featureKey("job-recommendation").enabled(true).build()
    );

    @BeforeEach
    void setup() {
        when(notificationSettingsService.getNotificationSettings(USERNAME)).thenReturn(notifResp);
        when(privacySettingsService.getPrivacySettings(USERNAME)).thenReturn(privacyResp);
        when(hrNotificationSettingsService.getHrNotificationSettings(USERNAME)).thenReturn(hrNotifResp);
        when(platformFeatureToggleService.listAll()).thenReturn(featureList);
    }

    /** Create the mock outside any when() chain to avoid nested-stubbing issues. */
    private UserEntity userWithRole(EUserRole role) {
        UserEntity u = mock(UserEntity.class);
        when(u.getUserRole()).thenReturn(role);
        return u;
    }

    // ---- getSettings ----

    @Test
    void getSettings_userNotFound_throwsNotFoundException() {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSettings(USERNAME))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getSettings_candidateRole_returnsBasicSettings() {
        UserEntity user = userWithRole(EUserRole.CANDIDATE);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

        SettingsResponse resp = service.getSettings(USERNAME);

        assertThat(resp.getRole()).isEqualTo("CANDIDATE");
        assertThat(resp.getNotifications()).isEqualTo(notifResp);
        assertThat(resp.getPrivacy()).isEqualTo(privacyResp);
        assertThat(resp.getHrNotifications()).isNull();
        assertThat(resp.getFeatures()).isNull();
    }

    @Test
    void getSettings_hrRole_includesHrNotifications() {
        UserEntity user = userWithRole(EUserRole.HR);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

        SettingsResponse resp = service.getSettings(USERNAME);

        assertThat(resp.getHrNotifications()).isEqualTo(hrNotifResp);
        assertThat(resp.getFeatures()).isNull();
        verify(hrNotificationSettingsService).getHrNotificationSettings(USERNAME);
    }

    @Test
    void getSettings_hrManagerRole_includesHrNotifications() {
        UserEntity user = userWithRole(EUserRole.HR_MANAGER);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

        SettingsResponse resp = service.getSettings(USERNAME);

        assertThat(resp.getHrNotifications()).isEqualTo(hrNotifResp);
        verify(hrNotificationSettingsService).getHrNotificationSettings(USERNAME);
    }

    @Test
    void getSettings_adminRole_includesFeatures() {
        UserEntity user = userWithRole(EUserRole.ADMIN);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

        SettingsResponse resp = service.getSettings(USERNAME);

        assertThat(resp.getFeatures()).isEqualTo(featureList);
        assertThat(resp.getHrNotifications()).isNull();
        verify(platformFeatureToggleService).listAll();
    }

    // ---- updateSettings ----

    @Test
    void updateSettings_userNotFound_throwsNotFoundException() {
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSettings(USERNAME, new SettingsRequest()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateSettings_nullNotificationsAndPrivacy_skipsUpdates() {
        UserEntity user = userWithRole(EUserRole.CANDIDATE);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

        service.updateSettings(USERNAME, SettingsRequest.builder().build());

        verify(notificationSettingsService, never()).updateNotificationSettings(anyString(), any());
        verify(privacySettingsService, never()).updatePrivacySettings(anyString(), any());
    }

    @Test
    void updateSettings_withNotificationsAndPrivacy_updatesAll() {
        UserEntity user = userWithRole(EUserRole.CANDIDATE);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        NotificationSettingsRequest notifReq = new NotificationSettingsRequest();
        PrivacySettingsRequest privacyReq = new PrivacySettingsRequest();

        service.updateSettings(USERNAME, SettingsRequest.builder()
                .notifications(notifReq).privacy(privacyReq).build());

        verify(notificationSettingsService).updateNotificationSettings(USERNAME, notifReq);
        verify(privacySettingsService).updatePrivacySettings(USERNAME, privacyReq);
    }

    @Test
    void updateSettings_hrRoleWithHrNotifications_updatesHrSettings() {
        UserEntity user = userWithRole(EUserRole.HR);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        HrNotificationSettingsRequest hrReq = new HrNotificationSettingsRequest();

        service.updateSettings(USERNAME, SettingsRequest.builder().hrNotifications(hrReq).build());

        verify(hrNotificationSettingsService).updateHrNotificationSettings(USERNAME, hrReq);
    }

    @Test
    void updateSettings_hrManagerWithHrNotifications_updatesHrSettings() {
        UserEntity user = userWithRole(EUserRole.HR_MANAGER);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        HrNotificationSettingsRequest hrReq = new HrNotificationSettingsRequest();

        service.updateSettings(USERNAME, SettingsRequest.builder().hrNotifications(hrReq).build());

        verify(hrNotificationSettingsService).updateHrNotificationSettings(USERNAME, hrReq);
    }

    @Test
    void updateSettings_candidateRoleWithHrNotifications_ignoresHrSettings() {
        UserEntity user = userWithRole(EUserRole.CANDIDATE);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

        service.updateSettings(USERNAME, SettingsRequest.builder()
                .hrNotifications(new HrNotificationSettingsRequest()).build());

        verify(hrNotificationSettingsService, never()).updateHrNotificationSettings(anyString(), any());
    }

    @Test
    void updateSettings_adminRoleWithFeatures_updatesEachToggle() {
        UserEntity user = userWithRole(EUserRole.ADMIN);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(platformFeatureToggleService.updateToggle(anyString(), anyBoolean(), eq(USERNAME)))
                .thenReturn(null);
        List<SettingsRequest.FeatureToggleEntry> entries = List.of(
                SettingsRequest.FeatureToggleEntry.builder().featureKey("job-recommendation").enabled(true).build(),
                SettingsRequest.FeatureToggleEntry.builder().featureKey("cv-referral").enabled(false).build()
        );

        service.updateSettings(USERNAME, SettingsRequest.builder().features(entries).build());

        verify(platformFeatureToggleService).updateToggle("job-recommendation", true, USERNAME);
        verify(platformFeatureToggleService).updateToggle("cv-referral", false, USERNAME);
    }

    @Test
    void updateSettings_nonAdminRoleWithFeatures_ignoresFeatures() {
        UserEntity user = userWithRole(EUserRole.HR);
        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

        service.updateSettings(USERNAME, SettingsRequest.builder()
                .features(List.of(SettingsRequest.FeatureToggleEntry.builder()
                        .featureKey("job-recommendation").enabled(true).build()))
                .build());

        verify(platformFeatureToggleService, never()).updateToggle(anyString(), anyBoolean(), anyString());
    }
}
