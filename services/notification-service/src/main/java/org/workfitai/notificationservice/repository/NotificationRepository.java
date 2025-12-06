package org.workfitai.notificationservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.workfitai.notificationservice.model.Notification;

import java.util.List;

/**
 * Repository for in-app notifications.
 */
@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    // Find notifications by user
    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<Notification> findByUserEmailOrderByCreatedAtDesc(String userEmail, Pageable pageable);

    // Find unread notifications
    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(String userId);

    List<Notification> findByUserEmailAndReadFalseOrderByCreatedAtDesc(String userEmail);

    // Count unread
    long countByUserIdAndReadFalse(String userId);

    long countByUserEmailAndReadFalse(String userEmail);

    // Find by reference
    List<Notification> findByReferenceIdAndReferenceType(String referenceId, String referenceType);

    // Find by source service
    Page<Notification> findBySourceServiceOrderByCreatedAtDesc(String sourceService, Pageable pageable);
}
