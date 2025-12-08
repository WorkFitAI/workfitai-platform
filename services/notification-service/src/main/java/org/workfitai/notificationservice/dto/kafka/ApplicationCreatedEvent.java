package org.workfitai.notificationservice.dto.kafka;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka event received when an application is created.
 *
 * Subscribed from: application-events topic (published by application-service)
 *
 * Purpose:
 * - Send confirmation email to candidate
 * - Send notification email to HR
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationCreatedEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("eventType")
    private String eventType;

    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("data")
    private ApplicationData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicationData {

        @JsonProperty("applicationId")
        private String applicationId;

        @JsonProperty("username")
        private String username;

        @JsonProperty("jobId")
        private String jobId;

        @JsonProperty("cvFileUrl")
        private String cvFileUrl;

        @JsonProperty("status")
        private String status;

        @JsonProperty("jobTitle")
        private String jobTitle;

        @JsonProperty("companyName")
        private String companyName;

        @JsonProperty("appliedAt")
        private Instant appliedAt;

        @JsonProperty("hrUsername")
        private String hrUsername;

        @JsonProperty("candidateName")
        private String candidateName;
    }
}
