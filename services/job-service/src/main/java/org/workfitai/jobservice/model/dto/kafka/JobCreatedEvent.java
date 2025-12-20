package org.workfitai.jobservice.model.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Event khi tạo job mới
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobCreatedEvent {

    private String eventType; // "JOB_CREATED"
    private Instant timestamp;
    private UUID jobId;
    private JobEventData data;
}
