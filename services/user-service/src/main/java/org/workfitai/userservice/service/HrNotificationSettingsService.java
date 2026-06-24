package org.workfitai.userservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.userservice.dto.request.HrNotificationSettingsRequest;
import org.workfitai.userservice.dto.response.HrNotificationSettingsResponse;
import org.workfitai.userservice.exception.BadRequestException;
import org.workfitai.userservice.exception.NotFoundException;
import org.workfitai.userservice.model.UserEntity;
import org.workfitai.userservice.repository.UserRepository;
import org.workfitai.userservice.util.JsonSettingsMerger;

/**
 * HR-only notification preferences (new-application / job-expiry emails).
 * Reuses the same notification_settings JSONB column as candidate settings
 * (HREntity extends UserEntity) - kept in a separate DTO/endpoint pair so
 * candidates have no visibility into or ability to set HR-only keys.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrNotificationSettingsService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public HrNotificationSettingsResponse getHrNotificationSettings(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        return readFromSettings(user.getNotificationSettings(), username);
    }

    /**
     * Get HR notification settings by email (for internal API consumers).
     */
    public HrNotificationSettingsResponse getHrNotificationSettingsByEmail(String email) {
        UserEntity user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            log.debug("No user found for email: {}, using defaults", email);
            return createDefaultSettings();
        }

        return readFromSettings(user.getNotificationSettings(), email);
    }

    @Transactional
    public HrNotificationSettingsResponse updateHrNotificationSettings(
            String username,
            HrNotificationSettingsRequest request) {

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        try {
            JsonNode currentSettings = user.getNotificationSettings();
            JsonNode newSettings = objectMapper.valueToTree(request);

            JsonNode mergedSettings = JsonSettingsMerger.merge(currentSettings, newSettings);

            user.setNotificationSettings(mergedSettings);
            userRepository.save(user);

            log.info("Updated HR notification settings for user: {}", username);

            return objectMapper.treeToValue(mergedSettings, HrNotificationSettingsResponse.class);

        } catch (JsonProcessingException e) {
            log.error("Error updating HR notification settings for user: {}", username, e);
            throw new BadRequestException("Failed to update HR notification settings");
        }
    }

    private HrNotificationSettingsResponse readFromSettings(JsonNode settings, String identifier) {
        if (settings == null) {
            return createDefaultSettings();
        }

        try {
            HrNotificationSettingsResponse parsed = objectMapper.treeToValue(settings,
                    HrNotificationSettingsResponse.class);
            // Fields may be absent on a first-touch row (candidate-only settings
            // saved before HR fields existed) - fall back to defaults per-field.
            return HrNotificationSettingsResponse.builder()
                    .notifyOnNewApplication(
                            parsed.getNotifyOnNewApplication() != null ? parsed.getNotifyOnNewApplication() : true)
                    .notifyOnJobExpiry(parsed.getNotifyOnJobExpiry() != null ? parsed.getNotifyOnJobExpiry() : true)
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Error parsing HR notification settings for: {}", identifier, e);
            return createDefaultSettings();
        }
    }

    private HrNotificationSettingsResponse createDefaultSettings() {
        return HrNotificationSettingsResponse.builder()
                .notifyOnNewApplication(true)
                .notifyOnJobExpiry(true)
                .build();
    }
}
