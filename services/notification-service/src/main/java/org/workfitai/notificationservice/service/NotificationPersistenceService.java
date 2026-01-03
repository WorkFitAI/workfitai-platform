package org.workfitai.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.workfitai.notificationservice.dto.kafka.NotificationEvent;
import org.workfitai.notificationservice.model.EmailLog;
import org.workfitai.notificationservice.model.Notification;
import org.workfitai.notificationservice.model.NotificationType;
import org.workfitai.notificationservice.repository.EmailLogRepository;
import org.workfitai.notificationservice.repository.NotificationRepository;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPersistenceService {

    private final EmailLogRepository emailLogRepository;
    private final NotificationRepository notificationRepository;
    private final RealtimeNotificationService realtimeService;

    /**
     * Save email delivery log
     */
    public EmailLog saveEmailLog(NotificationEvent event, boolean delivered, String error) {
        EmailLog log = EmailLog.builder()
                .eventId(event.getEventId())
                .eventType(event.getEventType())
                .timestamp(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                .recipientEmail(event.getRecipientEmail())
                .recipientRole(event.getRecipientRole())
                .subject(event.getSubject())
                .templateType(event.getTemplateType())
                .metadata(event.getMetadata())
                .delivered(delivered)
                .error(error)
                .sourceService(event.getSourceService())
                .build();
        return emailLogRepository.save(log);
    }

    /**
     * Create in-app notification and push to user in real-time
     */
    public Notification createNotification(NotificationEvent event) {
        NotificationType type = NotificationType.GENERAL;
        if (StringUtils.hasText(event.getNotificationType())) {
            type = NotificationType.fromValue(event.getNotificationType());
        }

        Notification notification = Notification.builder()
                .userId(event.getRecipientUserId())
                .userEmail(event.getRecipientEmail())
                .type(type)
                .title(event.getSubject())
                .message(event.getContent())
                .actionUrl(event.getActionUrl())
                .data(event.getMetadata())
                .sourceService(event.getSourceService())
                .referenceId(event.getReferenceId())
                .referenceType(event.getReferenceType())
                .read(false)
                .createdAt(Instant.now())
                .build();

        Notification saved = notificationRepository.save(notification);

        // Push notification to user via WebSocket in real-time
        // Use userId (username) instead of email for WebSocket destination
        try {
            String username = saved.getUserId() != null ? saved.getUserId() : saved.getUserEmail();
            realtimeService.pushToUser(username, saved);
            log.debug("Pushed notification to WebSocket: user={}, id={}", username, saved.getId());

            // Push updated unread count
            long unreadCount = getUnreadCount(username);
            realtimeService.pushUnreadCountUpdate(username, unreadCount);
            log.debug("Pushed unread count update: user={}, count={}", username, unreadCount);
        } catch (Exception e) {
            log.error("Failed to push notification via WebSocket: {}", e.getMessage());
            // Continue execution even if WebSocket push fails
        }

        return saved;
    }

    /**
     * Get notifications for a user by email
     */
    public Page<Notification> getNotificationsByEmail(String email, Pageable pageable) {
        return notificationRepository.findByUserEmailOrderByCreatedAtDesc(email, pageable);
    }

    /**
     * Get notifications for a user by userId (username)
     */
    public Page<Notification> getNotificationsByUserId(String userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Get unread notification count for a user by userId (username)
     * Note: We query by userId (username) not userEmail because WebSocket sessions
     * use username
     */
    public long getUnreadCount(String userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    /**
     * Get notification by ID
     */
    public Notification getNotificationById(String notificationId) {
        return notificationRepository.findById(notificationId).orElse(null);
    }

    /**
     * Mark notification as read and update unread count via WebSocket
     */
    public Notification markAsRead(String notificationId) {
        return notificationRepository.findById(notificationId)
                .map(n -> {
                    n.setRead(true);
                    n.setReadAt(Instant.now());
                    n.setUpdatedAt(Instant.now());
                    Notification saved = notificationRepository.save(n);

                    // Update unread count for user via WebSocket
                    try {
                        String username = saved.getUserId() != null ? saved.getUserId() : saved.getUserEmail();
                        long unreadCount = getUnreadCount(username);
                        realtimeService.pushUnreadCountUpdate(username, unreadCount);
                        log.debug("Pushed unread count update: user={}, count={}", username, unreadCount);
                    } catch (Exception e) {
                        log.error("Failed to push unread count update: {}", e.getMessage());
                    }

                    return saved;
                })
                .orElse(null);
    }

    /**
     * Mark all notifications as read and update unread count via WebSocket
     * 
     * @param userId The userId (username) to mark notifications for
     */
    public void markAllAsRead(String userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
        Instant now = Instant.now();
        unread.forEach(n -> {
            n.setRead(true);
            n.setReadAt(now);
            n.setUpdatedAt(now);
        });
        notificationRepository.saveAll(unread);

        // Push unread count update (should be 0 now)
        try {
            realtimeService.pushUnreadCountUpdate(userId, 0L);
            log.debug("Pushed unread count update after marking all read: user={}", userId);
        } catch (Exception e) {
            log.error("Failed to push unread count update: {}", e.getMessage());
        }
    }

    /**
     * Get latest email logs
     */
    public List<EmailLog> getLatestEmailLogs(int limit) {
        int pageSize = Math.max(1, Math.min(limit, 200));
        return emailLogRepository.findAll(PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "timestamp")))
                .getContent();
    }

    /**
     * Get failed email logs for retry
     */
    public List<EmailLog> getFailedEmailLogs() {
        return emailLogRepository.findByDeliveredFalseOrderByTimestampDesc();
    }

    /**
     * Save email log for application events
     */
    public EmailLog saveEmailLogForApplication(String applicationId, String email, String subject, boolean delivered,
            String error) {
        EmailLog log = EmailLog.builder()
                .eventId(applicationId)
                .eventType("APPLICATION_CREATED")
                .timestamp(Instant.now())
                .recipientEmail(email)
                .subject(subject)
                .templateType("application-notification")
                .delivered(delivered)
                .error(error)
                .sourceService("application-service")
                .build();
        return emailLogRepository.save(log);
    }
}
