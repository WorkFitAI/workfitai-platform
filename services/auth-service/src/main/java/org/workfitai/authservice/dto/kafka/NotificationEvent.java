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

    @JsonProperty("recipientRole")
    private String recipientRole;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("content")
    private String content;

    @JsonProperty("templateType")
    private String templateType;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;
}
