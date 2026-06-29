package org.workfitai.userservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.workfitai.userservice.dto.request.HrNotificationSettingsRequest;
import org.workfitai.userservice.dto.response.HrNotificationSettingsResponse;
import org.workfitai.userservice.exception.NotFoundException;
import org.workfitai.userservice.model.UserEntity;
import org.workfitai.userservice.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HrNotificationSettingsServiceTest {

    @Mock UserRepository userRepository;
    @Spy ObjectMapper objectMapper;
    @InjectMocks HrNotificationSettingsService service;

    private UserEntity user;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        user = mock(UserEntity.class);
    }

    // ---- getHrNotificationSettings ----

    @Test
    void getByUsername_userNotFound_throwsNotFoundException() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getHrNotificationSettings("unknown"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getByUsername_nullSettings_returnsDefaults() {
        when(userRepository.findByUsername("hruser")).thenReturn(Optional.of(user));
        when(user.getNotificationSettings()).thenReturn(null);

        HrNotificationSettingsResponse resp = service.getHrNotificationSettings("hruser");

        assertThat(resp.getNotifyOnNewApplication()).isTrue();
        assertThat(resp.getNotifyOnJobExpiry()).isTrue();
    }

    @Test
    void getByUsername_validSettings_parsesFields() throws Exception {
        JsonNode settings = mapper.readTree(
                "{\"notifyOnNewApplication\":false,\"notifyOnJobExpiry\":true}");
        when(userRepository.findByUsername("hruser")).thenReturn(Optional.of(user));
        when(user.getNotificationSettings()).thenReturn(settings);

        HrNotificationSettingsResponse resp = service.getHrNotificationSettings("hruser");

        assertThat(resp.getNotifyOnNewApplication()).isFalse();
        assertThat(resp.getNotifyOnJobExpiry()).isTrue();
    }

    // ---- getHrNotificationSettingsByEmail ----

    @Test
    void getByEmail_userNotFound_returnsDefaults() {
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        HrNotificationSettingsResponse resp = service.getHrNotificationSettingsByEmail("nobody@test.com");

        assertThat(resp.getNotifyOnNewApplication()).isTrue();
    }

    @Test
    void getByEmail_nullSettings_returnsDefaults() {
        when(userRepository.findByEmail("hr@test.com")).thenReturn(Optional.of(user));
        when(user.getNotificationSettings()).thenReturn(null);

        HrNotificationSettingsResponse resp = service.getHrNotificationSettingsByEmail("hr@test.com");

        assertThat(resp.getNotifyOnNewApplication()).isTrue();
    }

    @Test
    void getByEmail_validSettings_parsesFields() throws Exception {
        JsonNode settings = mapper.readTree(
                "{\"notifyOnNewApplication\":true,\"notifyOnJobExpiry\":false}");
        when(userRepository.findByEmail("hr@test.com")).thenReturn(Optional.of(user));
        when(user.getNotificationSettings()).thenReturn(settings);

        HrNotificationSettingsResponse resp = service.getHrNotificationSettingsByEmail("hr@test.com");

        assertThat(resp.getNotifyOnJobExpiry()).isFalse();
    }

    // ---- updateHrNotificationSettings ----

    @Test
    void update_userNotFound_throwsNotFoundException() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        HrNotificationSettingsRequest req = new HrNotificationSettingsRequest();
        assertThatThrownBy(() -> service.updateHrNotificationSettings("missing", req))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_success_savesAndReturnsResponse() throws Exception {
        JsonNode currentSettings = mapper.createObjectNode();
        when(userRepository.findByUsername("hruser")).thenReturn(Optional.of(user));
        when(user.getNotificationSettings()).thenReturn(currentSettings);
        when(userRepository.save(any())).thenReturn(user);

        HrNotificationSettingsRequest req = new HrNotificationSettingsRequest();
        req.setNotifyOnNewApplication(false);
        req.setNotifyOnJobExpiry(true);

        HrNotificationSettingsResponse resp = service.updateHrNotificationSettings("hruser", req);

        verify(userRepository).save(user);
        assertThat(resp).isNotNull();
    }
}
