package org.workfitai.userservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.userservice.dto.request.PrivacySettingsRequest;
import org.workfitai.userservice.dto.response.PrivacySettingsResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.enums.EUserStatus;
import org.workfitai.userservice.exception.BadRequestException;
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
class PrivacySettingsServiceTest {

    @Mock UserRepository userRepository;
    @Spy ObjectMapper objectMapper;

    @InjectMocks
    PrivacySettingsService service;

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

    // ---- getPrivacySettings ----

    @Test
    void getPrivacySettings_userNotFound_throws() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPrivacySettings("nobody"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getPrivacySettings_nullSettings_returnsDefault() {
        user.setPrivacySettings(null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        PrivacySettingsResponse resp = service.getPrivacySettings("testuser");

        assertThat(resp.getProfileVisibility()).isEqualTo("PUBLIC");
        assertThat(resp.getSearchIndexing()).isTrue();
        assertThat(resp.getAiJobRecommendationEnabled()).isFalse();
    }

    @Test
    void getPrivacySettings_validSettings_returnsParsed() throws Exception {
        String json = "{\"profileVisibility\":\"PRIVATE\",\"showEmail\":false,\"showPhone\":false," +
                "\"showLocation\":true,\"allowMessaging\":false,\"showActivityStatus\":true," +
                "\"showOnlineStatus\":true,\"searchIndexing\":false,\"aiJobRecommendationEnabled\":true}";
        user.setPrivacySettings(objectMapper.readTree(json));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        PrivacySettingsResponse resp = service.getPrivacySettings("testuser");

        assertThat(resp.getProfileVisibility()).isEqualTo("PRIVATE");
        assertThat(resp.getSearchIndexing()).isFalse();
    }

    // ---- updatePrivacySettings ----

    @Test
    void updatePrivacySettings_userNotFound_throws() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePrivacySettings("nobody", buildRequest(
                PrivacySettingsRequest.ProfileVisibility.PUBLIC, false)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updatePrivacySettings_privateWithSearchIndexing_throws() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        PrivacySettingsRequest req = buildRequest(PrivacySettingsRequest.ProfileVisibility.PRIVATE, true);

        assertThatThrownBy(() -> service.updatePrivacySettings("testuser", req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("search indexing");
    }

    @Test
    void updatePrivacySettings_publicWithSearchIndexing_success() throws Exception {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        PrivacySettingsRequest req = buildRequest(PrivacySettingsRequest.ProfileVisibility.PUBLIC, true);

        PrivacySettingsResponse resp = service.updatePrivacySettings("testuser", req);

        assertThat(resp).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    void updatePrivacySettings_privateWithoutSearchIndexing_success() throws Exception {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);

        PrivacySettingsRequest req = buildRequest(PrivacySettingsRequest.ProfileVisibility.PRIVATE, false);

        PrivacySettingsResponse resp = service.updatePrivacySettings("testuser", req);

        assertThat(resp).isNotNull();
        verify(userRepository).save(user);
    }

    // ---- getAiJobRecommendationConsentByUsername ----

    @Test
    void getAiJobRecommendationConsentByUsername_userNotFound_returnsFalse() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());

        assertThat(service.getAiJobRecommendationConsentByUsername("nobody")).isFalse();
    }

    @Test
    void getAiJobRecommendationConsentByUsername_nullSettings_returnsFalse() {
        user.setPrivacySettings(null);
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        assertThat(service.getAiJobRecommendationConsentByUsername("testuser")).isFalse();
    }

    @Test
    void getAiJobRecommendationConsentByUsername_enabledTrue_returnsTrue() throws Exception {
        String json = "{\"profileVisibility\":\"PUBLIC\",\"showEmail\":false,\"showPhone\":false," +
                "\"showLocation\":true,\"allowMessaging\":true,\"showActivityStatus\":true," +
                "\"showOnlineStatus\":true,\"searchIndexing\":true,\"aiJobRecommendationEnabled\":true}";
        user.setPrivacySettings(objectMapper.readTree(json));
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        assertThat(service.getAiJobRecommendationConsentByUsername("testuser")).isTrue();
    }

    // ---- helpers ----

    private PrivacySettingsRequest buildRequest(
            PrivacySettingsRequest.ProfileVisibility visibility,
            boolean searchIndexing) {
        return PrivacySettingsRequest.builder()
                .profileVisibility(visibility)
                .showEmail(false)
                .showPhone(false)
                .showLocation(true)
                .allowMessaging(true)
                .showActivityStatus(true)
                .showOnlineStatus(true)
                .searchIndexing(searchIndexing)
                .aiJobRecommendationEnabled(false)
                .build();
    }
}
