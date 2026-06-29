package org.workfitai.userservice.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.workfitai.userservice.dto.request.SettingsRequest;
import org.workfitai.userservice.dto.response.ResponseData;
import org.workfitai.userservice.dto.response.SettingsResponse;
import org.workfitai.userservice.service.SettingsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingsControllerTest {

    @Mock SettingsService settingsService;
    @InjectMocks SettingsController controller;

    private Authentication auth;
    private static final String USERNAME = "testuser";

    @BeforeEach
    void setup() {
        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(USERNAME);
    }

    @Test
    void getSettings_delegatesToService() {
        SettingsResponse response = SettingsResponse.builder().build();
        when(settingsService.getSettings(USERNAME)).thenReturn(response);

        ResponseEntity<ResponseData<SettingsResponse>> resp = controller.getSettings(auth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getData()).isEqualTo(response);
        verify(settingsService).getSettings(USERNAME);
    }

    @Test
    void updateSettings_delegatesToService() {
        SettingsRequest req = new SettingsRequest();
        SettingsResponse response = SettingsResponse.builder().build();
        when(settingsService.updateSettings(USERNAME, req)).thenReturn(response);

        ResponseEntity<ResponseData<SettingsResponse>> resp = controller.updateSettings(req, auth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(settingsService).updateSettings(USERNAME, req);
    }
}
