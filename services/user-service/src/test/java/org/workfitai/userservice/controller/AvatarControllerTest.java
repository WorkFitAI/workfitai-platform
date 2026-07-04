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
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.userservice.dto.response.AvatarResponse;
import org.workfitai.userservice.service.AvatarService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvatarControllerTest {

    @Mock AvatarService avatarService;
    @InjectMocks AvatarController controller;

    private Authentication auth;
    private static final String USERNAME = "testuser";

    @BeforeEach
    void setup() {
        auth = mock(Authentication.class);
        when(auth.getName()).thenReturn(USERNAME);
    }

    @Test
    void uploadAvatar_delegatesToService() {
        MultipartFile file = mock(MultipartFile.class);
        AvatarResponse response = AvatarResponse.builder().avatarUrl("https://cdn/avatar.png").build();
        when(avatarService.uploadAvatar(USERNAME, file)).thenReturn(response);

        ResponseEntity<AvatarResponse> resp = controller.uploadAvatar(file, auth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().getAvatarUrl()).isEqualTo("https://cdn/avatar.png");
        verify(avatarService).uploadAvatar(USERNAME, file);
    }

    @Test
    void deleteAvatar_delegatesToService() {
        doNothing().when(avatarService).deleteAvatar(USERNAME);

        ResponseEntity<Map<String, String>> resp = controller.deleteAvatar(auth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).containsKey("message");
        verify(avatarService).deleteAvatar(USERNAME);
    }

    @Test
    void getAvatar_delegatesToService() {
        AvatarResponse response = AvatarResponse.builder().avatarUrl("https://cdn/avatar.png").build();
        when(avatarService.getAvatar(USERNAME)).thenReturn(response);

        ResponseEntity<AvatarResponse> resp = controller.getAvatar(auth);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(avatarService).getAvatar(USERNAME);
    }
}
