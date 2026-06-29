package org.workfitai.jobservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.OutboxExpiredJobEvent;
import org.workfitai.jobservice.model.enums.EventStatus;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.repository.OutboxRepository;
import org.workfitai.jobservice.service.JobEventProducer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {

    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private JobEventProducer jobEventProducer;
    @Mock
    private JobRepository jobRepository;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks
    private OutboxProcessor outboxProcessor;

    private OutboxExpiredJobEvent event(String type, String payload) {
        OutboxExpiredJobEvent event = new OutboxExpiredJobEvent();
        event.setType(type);
        event.setPayload(payload);
        event.setRetryCount(0);
        event.setStatus(EventStatus.NEW);
        return event;
    }

    @Test
    void process_marksSent_whenJobExpiredHandledSuccessfully() throws Exception {
        UUID jobId = UUID.randomUUID();
        OutboxExpiredJobEvent event = event("JOB_EXPIRED",
                objectMapper.writeValueAsString(
                        new org.workfitai.jobservice.model.dto.kafka.JobExpiredEventDTO(jobId.toString(), "alice", "hr@test.com", "Java Dev", "FPT")));

        when(outboxRepository.findTop100ByStatusInOrderByCreatedDateAsc(any())).thenReturn(List.of(event));
        Job job = new Job();
        job.setJobId(jobId);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        outboxProcessor.process();

        assertThat(event.getStatus()).isEqualTo(EventStatus.SENT);
        verify(jobEventProducer).publishJobExpired(job);
        verify(outboxRepository).save(event);
    }

    @Test
    void process_marksSent_whenSendMailHandledSuccessfully() throws Exception {
        OutboxExpiredJobEvent event = event("SEND_MAIL",
                objectMapper.writeValueAsString(
                        new org.workfitai.jobservice.model.dto.kafka.JobExpiredEventDTO("job-1", "alice", "hr@test.com", "Java Dev", "FPT")));

        when(outboxRepository.findTop100ByStatusInOrderByCreatedDateAsc(any())).thenReturn(List.of(event));

        outboxProcessor.process();

        assertThat(event.getStatus()).isEqualTo(EventStatus.SENT);
        verify(notificationService).sendExpiredNotificationAsync(any());
    }

    @Test
    void process_marksFailedAndIncrementsRetry_whenJobIdMissing() throws Exception {
        OutboxExpiredJobEvent event = event("JOB_EXPIRED",
                objectMapper.writeValueAsString(
                        new org.workfitai.jobservice.model.dto.kafka.JobExpiredEventDTO(null, "alice", "hr@test.com", "Java Dev", "FPT")));

        when(outboxRepository.findTop100ByStatusInOrderByCreatedDateAsc(any())).thenReturn(List.of(event));

        outboxProcessor.process();

        assertThat(event.getStatus()).isEqualTo(EventStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(1);
        verify(jobRepository, never()).findById(any());
    }

    @Test
    void process_marksFailed_whenJobNotFound() throws Exception {
        UUID jobId = UUID.randomUUID();
        OutboxExpiredJobEvent event = event("JOB_EXPIRED",
                objectMapper.writeValueAsString(
                        new org.workfitai.jobservice.model.dto.kafka.JobExpiredEventDTO(jobId.toString(), "alice", "hr@test.com", "Java Dev", "FPT")));

        when(outboxRepository.findTop100ByStatusInOrderByCreatedDateAsc(any())).thenReturn(List.of(event));
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        outboxProcessor.process();

        assertThat(event.getStatus()).isEqualTo(EventStatus.FAILED);
        verify(jobEventProducer, never()).publishJobExpired(any());
    }

    @Test
    void process_marksDead_whenRetryCountReachesFive() throws Exception {
        OutboxExpiredJobEvent event = event("JOB_EXPIRED",
                objectMapper.writeValueAsString(
                        new org.workfitai.jobservice.model.dto.kafka.JobExpiredEventDTO(null, "alice", "hr@test.com", "Java Dev", "FPT")));
        event.setRetryCount(4);

        when(outboxRepository.findTop100ByStatusInOrderByCreatedDateAsc(any())).thenReturn(List.of(event));

        outboxProcessor.process();

        assertThat(event.getStatus()).isEqualTo(EventStatus.DEAD);
        assertThat(event.getRetryCount()).isEqualTo(5);
    }

    @Test
    void process_noOp_whenNoEventsPending() {
        when(outboxRepository.findTop100ByStatusInOrderByCreatedDateAsc(any())).thenReturn(List.of());

        outboxProcessor.process();

        verify(outboxRepository, never()).save(any());
    }

    @Test
    void process_handlesUnknownEventType_withoutThrowing() throws Exception {
        OutboxExpiredJobEvent event = event("UNKNOWN_TYPE",
                objectMapper.writeValueAsString(
                        new org.workfitai.jobservice.model.dto.kafka.JobExpiredEventDTO("job-1", "alice", "hr@test.com", "Java Dev", "FPT")));

        when(outboxRepository.findTop100ByStatusInOrderByCreatedDateAsc(any())).thenReturn(List.of(event));

        outboxProcessor.process();

        assertThat(event.getStatus()).isEqualTo(EventStatus.SENT);
        verify(jobEventProducer, never()).publishJobExpired(any());
        verify(notificationService, never()).sendExpiredNotificationAsync(any());
    }
}
