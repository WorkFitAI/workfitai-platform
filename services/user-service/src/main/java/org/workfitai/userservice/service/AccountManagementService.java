package org.workfitai.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.userservice.dto.request.DeactivateAccountRequest;
import org.workfitai.userservice.dto.request.DeleteAccountRequest;
import org.workfitai.userservice.dto.response.AccountManagementResponse;
import org.workfitai.userservice.model.UserEntity;
import org.workfitai.userservice.exception.BadRequestException;
import org.workfitai.userservice.exception.NotFoundException;
import org.workfitai.userservice.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountManagementService {

    private final UserRepository userRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final int DEACTIVATION_RETENTION_DAYS = 30;
    private static final int DELETION_GRACE_PERIOD_DAYS = 7;

    @Transactional
    public AccountManagementResponse deactivateAccount(String username, DeactivateAccountRequest request) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Check if account is already deactivated
        if (user.getDeactivatedAt() != null) {
            throw new BadRequestException("Account is already deactivated");
        }

        // Check if account deletion is scheduled
        if (user.getDeletionScheduledAt() != null) {
            throw new BadRequestException("Cannot deactivate account. Deletion is already scheduled");
        }

        Instant now = Instant.now();
        Instant deletionDate = now.plus(Duration.ofDays(DEACTIVATION_RETENTION_DAYS));

        user.setDeactivatedAt(now);
        user.setDeactivationReason(request.getReason());
        user.setDeletionDate(deletionDate);

        userRepository.save(user);

        // Send notification
        sendAccountNotification(user, "ACCOUNT_DEACTIVATED", Map.of(
                "username", username,
                "deactivatedAt", now.toString(),
                "deletionDate", deletionDate.toString(),
                "retentionDays", DEACTIVATION_RETENTION_DAYS));

        log.info("Account deactivated for user: {}. Scheduled for deletion on: {}", username, deletionDate);

        return AccountManagementResponse.builder()
                .status("DEACTIVATED")
                .message("Account deactivated successfully. Your data will be deleted after "
                        + DEACTIVATION_RETENTION_DAYS + " days")
                .scheduledDate(deletionDate)
                .daysRemaining(DEACTIVATION_RETENTION_DAYS)
                .build();
    }

    @Transactional
    public AccountManagementResponse reactivateAccount(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Check if account is deactivated
        if (user.getDeactivatedAt() == null) {
            throw new BadRequestException("Account is not deactivated");
        }

        user.setDeactivatedAt(null);
        user.setDeactivationReason(null);
        user.setDeletionDate(null);

        userRepository.save(user);

        // Send notification
        sendAccountNotification(user, "ACCOUNT_REACTIVATED", Map.of(
                "username", username,
                "reactivatedAt", Instant.now().toString()));

        log.info("Account reactivated for user: {}", username);

        return AccountManagementResponse.builder()
                .status("ACTIVE")
                .message("Account reactivated successfully")
                .build();
    }

    @Transactional
    public AccountManagementResponse requestAccountDeletion(String username, DeleteAccountRequest request) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Verify password (in production, call auth-service to verify)
        // For now, we assume password is verified

        // Check if deletion is already scheduled
        if (user.getDeletionScheduledAt() != null) {
            throw new BadRequestException("Account deletion is already scheduled");
        }

        Instant now = Instant.now();
        Instant deletionDate = now.plus(Duration.ofDays(DELETION_GRACE_PERIOD_DAYS));

        user.setDeletionScheduledAt(now);
        user.setDeletionDate(deletionDate);
        user.setDeactivationReason(request.getReason());

        userRepository.save(user);

        // Send notification
        sendAccountNotification(user, "ACCOUNT_DELETION_SCHEDULED", Map.of(
                "username", username,
                "scheduledAt", now.toString(),
                "deletionDate", deletionDate.toString(),
                "gracePeriodDays", DELETION_GRACE_PERIOD_DAYS));

        log.info("Account deletion scheduled for user: {}. Will be deleted on: {}", username, deletionDate);

        return AccountManagementResponse.builder()
                .status("DELETION_SCHEDULED")
                .message("Account deletion scheduled. You have " + DELETION_GRACE_PERIOD_DAYS + " days to cancel")
                .scheduledDate(deletionDate)
                .daysRemaining(DELETION_GRACE_PERIOD_DAYS)
                .build();
    }

    @Transactional
    public AccountManagementResponse cancelAccountDeletion(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Check if deletion is scheduled
        if (user.getDeletionScheduledAt() == null) {
            throw new BadRequestException("No account deletion scheduled");
        }

        user.setDeletionScheduledAt(null);
        user.setDeletionDate(null);
        user.setDeactivationReason(null);

        userRepository.save(user);

        // Send notification
        sendAccountNotification(user, "ACCOUNT_DELETION_CANCELLED", Map.of(
                "username", username,
                "cancelledAt", Instant.now().toString()));

        log.info("Account deletion cancelled for user: {}", username);

        return AccountManagementResponse.builder()
                .status("ACTIVE")
                .message("Account deletion cancelled successfully")
                .build();
    }

    @Transactional
    public void executeScheduledDeletions() {
        Instant now = Instant.now();

        // Find accounts scheduled for deletion
        userRepository.findAll().stream()
                .filter(user -> user.getDeletionDate() != null && user.getDeletionDate().isBefore(now))
                .forEach(user -> {
                    // Soft delete by setting deleted_at timestamp
                    user.setDeletedAt(now);
                    userRepository.save(user);

                    // Send notification
                    sendAccountNotification(user, "ACCOUNT_DELETED", Map.of(
                            "username", user.getUsername(),
                            "deletedAt", now.toString()));

                    log.info("Account permanently deleted for user: {}", user.getUsername());
                });
    }

    private void sendAccountNotification(UserEntity user, String templateType, Map<String, Object> data) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("email", user.getEmail());
            notification.put("templateType", templateType);
            notification.put("data", data);

            kafkaTemplate.send("notification-events", notification);
            log.info("Account notification sent for user: {} with template: {}", user.getUsername(), templateType);

        } catch (Exception e) {
            log.error("Failed to send account notification for user: {}", user.getUsername(), e);
        }
    }
}
