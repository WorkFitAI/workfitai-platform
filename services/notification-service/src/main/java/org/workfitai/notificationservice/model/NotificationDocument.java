package org.workfitai.notificationservice.model;

import java.time.Instant;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDocument {
    @Id
    private String id;
    private String eventId;
    private String eventType;
    private Instant timestamp;
    private String recipientEmail;
    private String recipientRole;
    private String subject;
    private String content;
    private Map<String, Object> metadata;
    private boolean delivered;
    private String error;
}
