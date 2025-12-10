package org.workfitai.applicationservice.dto.kafka;

import java.time.Instant;

import org.workfitai.applicationservice.model.enums.ApplicationStatus;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka event published when an application status changes.
 * 
 * Published to: application-status topic
 * 
 * Consumers:
 * - notification-service: Send status update email to candidate
 * - analytics-service: Track pipeline metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationStatusChangedEvent {

    @NotBlank
    @JsonProperty("eventId")
    private String eventId;

    @NotBlank
    @JsonProperty("eventType")
    @Builder.Default
    private String eventType = "STATUS_CHANGED";

    @NotNull
    @JsonProperty("timestamp")
    private Instant timestamp;

    @NotNull
    @JsonProperty("data")
    private StatusChangeData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusChangeData {

        @NotBlank
        @JsonProperty("applicationId")
        private String applicationId;

        @NotBlank
        @JsonProperty("username")
        private String username;

        @NotBlank
        @JsonProperty("jobId")
        private String jobId;

        @NotNull
        @JsonProperty("previousStatus")
        private ApplicationStatus previousStatus;

        @NotNull
        @JsonProperty("newStatus")
        private ApplicationStatus newStatus;

        @JsonProperty("jobTitle")
        private String jobTitle;

        @JsonProperty("companyName")
        private String companyName;

        @JsonProperty("changedBy")
        private String changedBy;

        @NotNull
        @JsonProperty("changedAt")
        private Instant changedAt;
    }
}
