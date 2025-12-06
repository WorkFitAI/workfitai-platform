package org.workfitai.authservice.dto.kafka;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("timestamp")
    @Builder.Default
    private Instant timestamp = Instant.now();

    @JsonProperty("recipientEmail")
    private String recipientEmail;

    // User ID for in-app notifications
    @JsonProperty("recipientUserId")
    private String recipientUserId;

    @JsonProperty("recipientRole")
    private String recipientRole;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("content")
    private String content;

    @JsonProperty("templateType")
    private String templateType;

    // Type of in-app notification (optional)
    @JsonProperty("notificationType")
    private String notificationType;

    // Whether to send email (default true)
    @JsonProperty("sendEmail")
    @Builder.Default
    private Boolean sendEmail = true;

    // Whether to create in-app notification (default false for backward
    // compatibility)
    @JsonProperty("createInAppNotification")
    @Builder.Default
    private Boolean createInAppNotification = false;

    // Action URL for in-app notification
    @JsonProperty("actionUrl")
    private String actionUrl;

    // Reference ID for linking to entities (job, application, etc.)
    @JsonProperty("referenceId")
    private String referenceId;

    // Reference type (JOB, APPLICATION, CV, etc.)
    @JsonProperty("referenceType")
    private String referenceType;

    // Source service that published this event
    @JsonProperty("sourceService")
    private String sourceService;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;
}
