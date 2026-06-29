package org.workfitai.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.userservice.dto.request.SettingsRequest;
import org.workfitai.userservice.dto.response.SettingsResponse;
import org.workfitai.userservice.enums.EUserRole;
import org.workfitai.userservice.exception.NotFoundException;
import org.workfitai.userservice.model.UserEntity;
import org.workfitai.userservice.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final UserRepository userRepository;
    private final NotificationSettingsService notificationSettingsService;
    private final PrivacySettingsService privacySettingsService;
    private final HrNotificationSettingsService hrNotificationSettingsService;
    private final PlatformFeatureToggleService platformFeatureToggleService;

    public SettingsResponse getSettings(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        EUserRole role = user.getUserRole();

        SettingsResponse.SettingsResponseBuilder builder = SettingsResponse.builder()
                .role(role.name())
                .notifications(notificationSettingsService.getNotificationSettings(username))
                .privacy(privacySettingsService.getPrivacySettings(username));

        if (role == EUserRole.HR || role == EUserRole.HR_MANAGER) {
            builder.hrNotifications(hrNotificationSettingsService.getHrNotificationSettings(username));
        }

        if (role == EUserRole.ADMIN) {
            builder.features(platformFeatureToggleService.listAll());
        }

        return builder.build();
    }

    @Transactional
    public SettingsResponse updateSettings(String username, SettingsRequest request) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        EUserRole role = user.getUserRole();

        if (request.getNotifications() != null) {
            notificationSettingsService.updateNotificationSettings(username, request.getNotifications());
        }

        if (request.getPrivacy() != null) {
            privacySettingsService.updatePrivacySettings(username, request.getPrivacy());
        }

        if (request.getHrNotifications() != null && (role == EUserRole.HR || role == EUserRole.HR_MANAGER)) {
            hrNotificationSettingsService.updateHrNotificationSettings(username, request.getHrNotifications());
        }

        if (request.getFeatures() != null && role == EUserRole.ADMIN) {
            for (SettingsRequest.FeatureToggleEntry entry : request.getFeatures()) {
                platformFeatureToggleService.updateToggle(entry.getFeatureKey(), entry.getEnabled(), username);
            }
        }

        return getSettings(username);
    }
}
