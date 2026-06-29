package org.workfitai.jobservice.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import org.workfitai.jobservice.model.dto.kafka.JobStatsUpdateEvent;
import org.workfitai.jobservice.service.impl.JobService;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobStatsConsumerTest {

    @Mock
    private JobService jobService;
    @Mock
    private Acknowledgment acknowledgment;
    @InjectMocks
    private JobStatsConsumer jobStatsConsumer;

    @Test
    void handleJobStatsUpdate_updatesStatsAndAcknowledges_onSuccess() {
        UUID jobId = UUID.randomUUID();
        JobStatsUpdateEvent event = JobStatsUpdateEvent.builder()
                .jobId(jobId).totalApplications(1).operation("INCREMENT").build();

        jobStatsConsumer.handleJobStatsUpdate(event, "job-stats-update", acknowledgment);

        verify(jobService).updateStats(jobId, 1, "INCREMENT");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleJobStatsUpdate_doesNotAcknowledge_whenServiceThrows() {
        UUID jobId = UUID.randomUUID();
        JobStatsUpdateEvent event = JobStatsUpdateEvent.builder()
                .jobId(jobId).totalApplications(1).operation("INCREMENT").build();
        doThrow(new RuntimeException("db down")).when(jobService).updateStats(jobId, 1, "INCREMENT");

        jobStatsConsumer.handleJobStatsUpdate(event, "job-stats-update", acknowledgment);

        verify(acknowledgment, never()).acknowledge();
    }
}
