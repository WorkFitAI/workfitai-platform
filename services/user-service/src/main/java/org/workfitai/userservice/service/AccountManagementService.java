package org.workfitai.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.userservice.dto.request.DeactivateAccountRequest;
import org.workfitai.userservice.dto.request.DeleteAccountRequest;
import org.workfitai.userservice.dto.response.AccountManagementResponse;
import org.workfitai.userservice.dto.kafka.NotificationEvent;
import org.workfitai.userservice.messaging.NotificationProducer;
import org.workfitai.userservice.model.UserEntity;
import org.workfitai.userservice.exception.BadRequestException;
import org.workfitai.userservice.exception.NotFoundException;
import org.workfitai.userservice.repository.UserRepository;
import org.workfitai.userservice.util.LogContext;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountManagementService {

    private final UserRepository userRepository;
    private final NotificationProducer notificationProducer;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.account.deactivation-retention-days:30}")
    private int deactivationRetentionDays;

    @Value("${app.account.deletion-grace-period-days:7}")
    private int deletionGracePeriodDays;

    @Transactional
    public AccountManagementResponse deactivateAccount(String username, DeactivateAccountRequest request) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        LogContext.setAction("DEACTIVATE_ACCOUNT");
        LogContext.setEntityType("User");
        LogContext.setEntityId(user.getId().toString());

        // Check if account is already deactivated
        if (user.getDeactivatedAt() != null) {
            throw new BadRequestException("Account is already deactivated");
        }

        // Check if account deletion is scheduled
        if (user.getDeletionScheduledAt() != null) {
            throw new BadRequestException("Cannot deactivate account. Deletion is already scheduled");
        }

        Instant now = Instant.now();
        Instant deletionDate = now.plus(Duration.ofDays(deactivationRetentionDays));

        user.setDeactivatedAt(now);
        user.setDeactivationReason(request.getReason());
        user.setDeletionDate(deletionDate);

        userRepository.save(user);

        // Send notification
        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("deactivatedAt", now.toString());
        data.put("daysRemaining", String.valueOf(deactivationRetentionDays));
        data.put("deletionDate", deletionDate.toString());
        data.put("reactivateUrl", "https://workfitai.com/profile/reactivate");

        sendAccountNotification(user, "account-deactivated", data);

        log.info("Account deactivated for user: {}. Scheduled for deletion on: {}", username, deletionDate);

        return AccountManagementResponse.builder()
                .status("DEACTIVATED")
                .message("Account deactivated successfully. Your data will be deleted after "
                        + deactivationRetentionDays + " days")
                .scheduledDate(deletionDate)
                .daysRemaining(deactivationRetentionDays)
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
        Instant deletionDate = now.plus(Duration.ofDays(deletionGracePeriodDays));

        user.setDeletionScheduledAt(now);
        user.setDeletionDate(deletionDate);
        user.setDeactivationReason(request.getReason());

        userRepository.save(user);

        // Send notification
        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("daysRemaining", String.valueOf(deletionGracePeriodDays));
        data.put("deletionDate", deletionDate.toString());
        data.put("cancelUrl", "https://workfitai.com/profile/cancel-deletion");

        sendAccountNotification(user, "account-deletion-requested", data);

        log.info("Account deletion scheduled for user: {}. Will be deleted on: {}", username, deletionDate);

        return AccountManagementResponse.builder()
                .status("DELETION_SCHEDULED")
                .message("Account deletion scheduled. You have " + deletionGracePeriodDays + " days to cancel")
                .scheduledDate(deletionDate)
                .daysRemaining(deletionGracePeriodDays)
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
            NotificationEvent event = NotificationEvent.builder()
                    .eventId(java.util.UUID.randomUUID().toString())
                    .eventType(templateType)
                    .recipientEmail(user.getEmail())
                    .recipientUserId(user.getUsername()) // Add userId for WebSocket push
                    .recipientRole(user.getUserRole() != null ? user.getUserRole().name() : "USER")
                    .subject("Account " + (templateType.equals("ACCOUNT_DELETED") ? "Deleted" : "Notification"))
                    .content(templateType.equals("ACCOUNT_DELETED")
                            ? "Your account has been permanently deleted."
                            : "Account status update.")
                    .templateType(templateType)
                    .notificationType(templateType.toLowerCase()) // Add notification type
                    .sendEmail(true)
                    .createInAppNotification(true)
                    .metadata(data)
                    .build();

            notificationProducer.send(event);
            log.info("Account notification sent for user: {} with template: {}", user.getUsername(), templateType);

        } catch (Exception e) {
            log.error("Failed to send account notification for user: {}", user.getUsername(), e);
        }
    }
}
