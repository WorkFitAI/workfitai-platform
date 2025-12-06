package org.workfitai.jobservice.model.dto.kafka;

import lombok.Data;

import java.util.UUID;

@Data
public class JobStatsUpdateEvent {
    private UUID jobId;
    private int totalApplications;
}
