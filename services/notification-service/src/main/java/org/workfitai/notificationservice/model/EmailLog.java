package org.workfitai.notificationservice.model;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Stores email delivery history/logs.
 * This tracks all emails sent through the notification service.
 */
@Document(collection = "email_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailLog {
    @Id
    private String id;

    @Indexed
    private String eventId;

    private String eventType;

    @Indexed
    private Instant timestamp;

    @Indexed
    private String recipientEmail;

    private String recipientRole;

    private String subject;

    private String templateType;

    private Map<String, Object> metadata;

    private boolean delivered;

    private String error;

    // Source service that triggered the email
    @Indexed
    private String sourceService;
}
