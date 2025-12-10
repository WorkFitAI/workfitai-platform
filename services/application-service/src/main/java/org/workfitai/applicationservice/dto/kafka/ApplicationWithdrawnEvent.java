package org.workfitai.applicationservice.dto.kafka;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka event published when an application is withdrawn.
 * 
 * Published to: application-events topic
 * 
 * Consumers:
 * - job-service: Decrease applicant count for the job
 * - notification-service: Send withdrawal confirmation to candidate
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationWithdrawnEvent {

    @NotBlank
    @JsonProperty("eventId")
    private String eventId;

    @NotBlank
    @JsonProperty("eventType")
    @Builder.Default
    private String eventType = "APPLICATION_WITHDRAWN";

    @NotNull
    @JsonProperty("timestamp")
    private Instant timestamp;

    @NotNull
    @JsonProperty("data")
    private WithdrawalData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WithdrawalData {

        @NotBlank
        @JsonProperty("applicationId")
        private String applicationId;

        @NotBlank
        @JsonProperty("username")
        private String username;

        @NotBlank
        @JsonProperty("jobId")
        private String jobId;

        @JsonProperty("jobTitle")
        private String jobTitle;

        @JsonProperty("companyName")
        private String companyName;

        @NotNull
        @JsonProperty("withdrawnAt")
        private Instant withdrawnAt;
    }
}
