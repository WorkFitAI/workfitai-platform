package org.workfitai.applicationservice.dto.kafka;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kafka event to update job statistics after application creation.
 *
 * Published to: job-stats-update topic
 *
 * Consumer:
 * - job-service: Update totalApplications count for the job
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStatsUpdateEvent {

    @NotBlank
    @JsonProperty("eventId")
    private String eventId;

    @NotNull
    @JsonProperty("jobId")
    private UUID jobId;

    @NotNull
    @JsonProperty("totalApplications")
    private Integer totalApplications;

    @NotNull
    @JsonProperty("timestamp")
    private Instant timestamp;

    @JsonProperty("operation")
    @Builder.Default
    private String operation = "INCREMENT"; // INCREMENT, DECREMENT, SET
}
