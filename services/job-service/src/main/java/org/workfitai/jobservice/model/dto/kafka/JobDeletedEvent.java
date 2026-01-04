package org.workfitai.jobservice.model.dto.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Event khi xóa/đóng job
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDeletedEvent {

    private String eventType; // "JOB_DELETED"
    private Instant timestamp;
    private UUID jobId;
    private String reason; // EXPIRED, HR_REMOVED, ADMIN_FLAGGED
    private String status; // CLOSED, REMOVED
}
