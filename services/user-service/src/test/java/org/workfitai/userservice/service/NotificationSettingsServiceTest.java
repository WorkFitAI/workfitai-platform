package org.workfitai.userservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.userservice.dto.request.NotificationSettingsRequest;
import org.workfitai.userservice.dto.response.NotificationSettingsResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.exception.NotFoundException;
import org.workfitai.userservice.model.CandidateEntity;
import org.workfitai.userservice.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSettingsServiceTest {

    @Mock UserRepository userRepository;
    @Spy ObjectMapper objectMapper;

    @InjectMocks
    NotificationSettingsService service;

    private CandidateEntity user;

    @BeforeEach
    void setUp() {
        user = CandidateEntity.builder()
                .userId(UUID.randomUUID())
                .email("u@test.com")
                .username("testuser")
                .userRole(EUserRole.CANDIDATE)
                .userStatus(EUserStatus.ACTIVE)
                .build();
    }

    // ---- getNotificationSettings ----

    @Test
    void getNotificationSettings_userNotFound_throws() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getNotificationSettings("nobody"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getNotificationSettings_nullSettings_returnsDefault() {
        user.setNotificationSettings(null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        NotificationSettingsResponse resp = service.getNotificationSettings("testuser");

        assertThat(resp.getEmail()).isNotNull();
        assertThat(resp.getPush()).isNotNull();
        assertThat(resp.getEmail().getJobAlerts()).isTrue();
        assertThat(resp.getEmail().getNewsletter()).isFalse();
    }

    @Test
    void getNotificationSettings_validSettings_returnsParsed() throws Exception {
        String json = "{\"email\":{\"jobAlerts\":false,\"applicationUpdates\":true,\"messages\":true,\"newsletter\":false,\"marketingEmails\":false,\"securityAlerts\":true}," +
                "\"push\":{\"jobAlerts\":true,\"applicationUpdates\":true,\"messages\":true,\"reminders\":false}}";
        user.setNotificationSettings(objectMapper.readTree(json));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        NotificationSettingsResponse resp = service.getNotificationSettings("testuser");

        assertThat(resp.getEmail().getJobAlerts()).isFalse();
        assertThat(resp.getPush().getReminders()).isFalse();
    }

    // ---- getNotificationSettingsByEmail ----

    @Test
    void getNotificationSettingsByEmail_noUser_returnsDefault() {
        when(userRepository.findByEmail("x@test.com")).thenReturn(Optional.empty());

        NotificationSettingsResponse resp = service.getNotificationSettingsByEmail("x@test.com");

        assertThat(resp.getEmail()).isNotNull();
    }

    @Test
    void getNotificationSettingsByEmail_nullSettings_returnsDefault() {
        user.setNotificationSettings(null);
        when(userRepository.findByEmail("u@test.com")).thenReturn(Optional.of(user));

        NotificationSettingsResponse resp = service.getNotificationSettingsByEmail("u@test.com");

        assertThat(resp.getEmail().getSecurityAlerts()).isTrue();
    }

    // ---- updateNotificationSettings ----

    @Test
    void updateNotificationSettings_userNotFound_throws() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateNotificationSettings("nobody", new NotificationSettingsRequest()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateNotificationSettings_success_mergesAndSaves() throws Exception {
        String existingJson = "{\"email\":{\"jobAlerts\":true,\"applicationUpdates\":true,\"messages\":true,\"newsletter\":false,\"marketingEmails\":false,\"securityAlerts\":true}," +
                "\"push\":{\"jobAlerts\":true,\"applicationUpdates\":true,\"messages\":true,\"reminders\":true}}";
        user.setNotificationSettings(objectMapper.readTree(existingJson));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        NotificationSettingsRequest req = new NotificationSettingsRequest();
        req.setEmail(NotificationSettingsRequest.EmailNotifications.builder()
                .jobAlerts(false)
                .build());

        NotificationSettingsResponse resp = service.updateNotificationSettings("testuser", req);

        assertThat(resp).isNotNull();
        verify(userRepository).save(user);
    }
}
