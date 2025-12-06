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
 * Kafka event published when an application is created.
 * 
 * Published to: application-events topic
 * 
 * Consumers:
 * - job-service: Update applicant count for the job
 * - notification-service: Send confirmation email to candidate
 * - analytics-service: Track application metrics
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationCreatedEvent {

    @NotBlank
    @JsonProperty("eventId")
    private String eventId;

    @NotBlank
    @JsonProperty("eventType")
    @Builder.Default
    private String eventType = "APPLICATION_CREATED";

    @NotNull
    @JsonProperty("timestamp")
    private Instant timestamp;

    @NotNull
    @JsonProperty("data")
    private ApplicationData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicationData {

        @NotBlank
        @JsonProperty("applicationId")
        private String applicationId;

        @NotBlank
        @JsonProperty("username")
        private String username;

        @NotBlank
        @JsonProperty("jobId")
        private String jobId;

        @JsonProperty("cvFileUrl")
        private String cvFileUrl;

        @NotNull
        @JsonProperty("status")
        private ApplicationStatus status;

        @JsonProperty("jobTitle")
        private String jobTitle;

        @JsonProperty("companyName")
        private String companyName;

        @NotNull
        @JsonProperty("appliedAt")
        private Instant appliedAt;
    }
}
