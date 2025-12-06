package org.workfitai.notificationservice.model;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * In-app notifications for users.
 * These are notifications shown in the user's notification center.
 */
@Document(collection = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "user_read_idx", def = "{'userId': 1, 'read': 1}")
public class Notification {
    @Id
    private String id;

    // User who receives the notification
    @Indexed
    private String userId;

    // User's email (for lookup if userId not available)
    @Indexed
    private String userEmail;

    // Notification category
    private NotificationType type;

    // Display title
    private String title;

    // Notification message
    private String message;

    // Link to navigate when clicked (optional)
    private String actionUrl;

    // Additional data for the notification
    private Map<String, Object> data;

    // Source service that created the notification
    @Indexed
    private String sourceService;

    // Reference ID (e.g., application ID, job ID, etc.)
    private String referenceId;

    // Reference type (e.g., "APPLICATION", "JOB", "CV")
    private String referenceType;

    // Whether the notification has been read
    @Indexed
    private boolean read;

    // When the notification was read
    private Instant readAt;

    @Indexed
    @Builder.Default
    private Instant createdAt = Instant.now();

    private Instant updatedAt;
}
