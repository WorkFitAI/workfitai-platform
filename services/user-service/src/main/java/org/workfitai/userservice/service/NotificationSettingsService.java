package org.workfitai.userservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.userservice.dto.request.NotificationSettingsRequest;
import org.workfitai.userservice.dto.response.NotificationSettingsResponse;
import org.workfitai.userservice.model.UserEntity;
import org.workfitai.userservice.exception.BadRequestException;
import org.workfitai.userservice.exception.NotFoundException;
import org.workfitai.userservice.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public NotificationSettingsResponse getNotificationSettings(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (user.getNotificationSettings() == null) {
            return createDefaultSettings();
        }

        try {
            return objectMapper.treeToValue(user.getNotificationSettings(), NotificationSettingsResponse.class);
        } catch (JsonProcessingException e) {
            log.error("Error parsing notification settings for user: {}", username, e);
            return createDefaultSettings();
        }
    }

    @Transactional
    public NotificationSettingsResponse updateNotificationSettings(
            String username,
            NotificationSettingsRequest request) {

        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        try {
            JsonNode currentSettings = user.getNotificationSettings();
            JsonNode newSettings = objectMapper.valueToTree(request);

            // Merge settings (partial update)
            JsonNode mergedSettings = mergeSettings(currentSettings, newSettings);

            user.setNotificationSettings(mergedSettings);
            userRepository.save(user);

            log.info("Updated notification settings for user: {}", username);

            return objectMapper.treeToValue(mergedSettings, NotificationSettingsResponse.class);

        } catch (JsonProcessingException e) {
            log.error("Error updating notification settings for user: {}", username, e);
            throw new BadRequestException("Failed to update notification settings");
        }
    }

    private JsonNode mergeSettings(JsonNode current, JsonNode update) {
        if (current == null || current.isNull()) {
            return update;
        }

        ObjectNode merged = current.deepCopy();

        update.fields().forEachRemaining(entry -> {
            String fieldName = entry.getKey();
            JsonNode value = entry.getValue();

            if (value.isObject() && merged.has(fieldName) && merged.get(fieldName).isObject()) {
                // Recursively merge nested objects
                JsonNode mergedNested = mergeSettings(merged.get(fieldName), value);
                merged.set(fieldName, mergedNested);
            } else if (!value.isNull()) {
                // Update field only if new value is not null
                merged.set(fieldName, value);
            }
        });

        return merged;
    }

    private NotificationSettingsResponse createDefaultSettings() {
        return NotificationSettingsResponse.builder()
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
                .sms(NotificationSettingsResponse.SmsNotifications.builder()
                        .jobAlerts(false)
                        .securityAlerts(true)
                        .importantUpdates(true)
                        .build())
                .build();
    }
}
