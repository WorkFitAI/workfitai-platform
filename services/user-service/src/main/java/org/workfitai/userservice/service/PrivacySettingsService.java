package org.workfitai.userservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.userservice.dto.request.PrivacySettingsRequest;
import org.workfitai.userservice.dto.response.PrivacySettingsResponse;
import org.workfitai.userservice.model.UserEntity;
import org.workfitai.userservice.exception.BadRequestException;
import org.workfitai.userservice.exception.NotFoundException;
import org.workfitai.userservice.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrivacySettingsService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public PrivacySettingsResponse getPrivacySettings(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (user.getPrivacySettings() == null) {
            return createDefaultSettings();
        }

        try {
            return objectMapper.treeToValue(user.getPrivacySettings(), PrivacySettingsResponse.class);
        } catch (JsonProcessingException e) {
            log.error("Error parsing privacy settings for user: {}", username, e);
            return createDefaultSettings();
        }
    }

    @Transactional
    public PrivacySettingsResponse updatePrivacySettings(
            String username,
            PrivacySettingsRequest request) {

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Validate settings
        validatePrivacySettings(request);

        try {
            JsonNode newSettings = objectMapper.valueToTree(request);
            user.setPrivacySettings(newSettings);
            userRepository.save(user);

            log.info("Updated privacy settings for user: {}", username);

            return objectMapper.treeToValue(newSettings, PrivacySettingsResponse.class);

        } catch (JsonProcessingException e) {
            log.error("Error updating privacy settings for user: {}", username, e);
            throw new BadRequestException("Failed to update privacy settings");
        }
    }

    private void validatePrivacySettings(PrivacySettingsRequest request) {
        // Additional business logic validation can be added here
        // For example: if profile is PRIVATE, certain fields must be false

        if (request.getProfileVisibility() == PrivacySettingsRequest.ProfileVisibility.PRIVATE) {
            if (request.getSearchIndexing()) {
                throw new BadRequestException("Cannot enable search indexing for private profiles");
            }
        }

        // Validate that if CV download is allowed, profile must be at least
        // RECRUITERS_ONLY
        if (request.getAllowCvDownload() &&
                request.getProfileVisibility() == PrivacySettingsRequest.ProfileVisibility.PRIVATE) {
            throw new BadRequestException("Cannot allow CV download for private profiles");
        }
    }

    private PrivacySettingsResponse createDefaultSettings() {
        return PrivacySettingsResponse.builder()
                .profileVisibility("PUBLIC")
                .showEmail(false)
                .showPhone(false)
                .showLocation(true)
                .allowCvDownload(true)
                .allowMessaging(true)
                .showActivityStatus(true)
                .showOnlineStatus(true)
                .searchIndexing(true)
                .build();
    }
}
