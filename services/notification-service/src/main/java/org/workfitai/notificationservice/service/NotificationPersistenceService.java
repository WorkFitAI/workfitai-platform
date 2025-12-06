package org.workfitai.notificationservice.service;

import lombok.RequiredArgsConstructor;
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
public class NotificationPersistenceService {

    private final EmailLogRepository emailLogRepository;
    private final NotificationRepository notificationRepository;

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
     * Create in-app notification
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
        return notificationRepository.save(notification);
    }

    /**
     * Get notifications for a user by email
     */
    public Page<Notification> getNotificationsByEmail(String email, Pageable pageable) {
        return notificationRepository.findByUserEmailOrderByCreatedAtDesc(email, pageable);
    }

    /**
     * Get notifications for a user by ID
     */
    public Page<Notification> getNotificationsByUserId(String userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Get unread notification count
     */
    public long getUnreadCount(String userEmail) {
        return notificationRepository.countByUserEmailAndReadFalse(userEmail);
    }

    /**
     * Mark notification as read
     */
    public Notification markAsRead(String notificationId) {
        return notificationRepository.findById(notificationId)
                .map(n -> {
                    n.setRead(true);
                    n.setReadAt(Instant.now());
                    n.setUpdatedAt(Instant.now());
                    return notificationRepository.save(n);
                })
                .orElse(null);
    }

    /**
     * Mark all notifications as read for a user
     */
    public void markAllAsRead(String userEmail) {
        List<Notification> unread = notificationRepository.findByUserEmailAndReadFalseOrderByCreatedAtDesc(userEmail);
        Instant now = Instant.now();
        unread.forEach(n -> {
            n.setRead(true);
            n.setReadAt(now);
            n.setUpdatedAt(now);
        });
        notificationRepository.saveAll(unread);
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
}
