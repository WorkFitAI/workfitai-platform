package org.workfitai.jobservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.OutboxExpiredJobEvent;
import org.workfitai.jobservice.model.dto.kafka.JobExpiredEventDTO;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.service.impl.JobService;
import org.workfitai.jobservice.service.impl.OutboxService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoUpdateJobSchedulerTest {

    @Mock
    private JobService jobService;
    @Mock
    private OutboxService outboxService;
    @InjectMocks
    private AutoUpdateJobScheduler scheduler;

    private Job job(String createdBy) {
        Job job = new Job();
        job.setJobId(UUID.randomUUID());
        job.setCreatedBy(createdBy);
        job.setStatus(JobStatus.PUBLISHED);
        return job;
    }

    @Test
    void closeExpiredJobs_noOp_whenNoneExpired() {
        when(jobService.findExpiredJobsToClose()).thenReturn(List.of());

        scheduler.closeExpiredJobs();

        verify(jobService, never()).fetchEmailMap(any());
        verify(jobService, never()).updateJobsAndEvents(any(), any());
    }

    @Test
    void closeExpiredJobs_closesJobsAndQueuesStatusAndMailEvents_whenHrWantsEmail() {
        Job job = job("alice");
        when(jobService.findExpiredJobsToClose()).thenReturn(List.of(job));
        when(jobService.fetchEmailMap(List.of(job))).thenReturn(Map.of("alice", "hr@test.com"));
        when(jobService.fetchHrJobExpirySettingsMap(any())).thenReturn(Map.of("hr@test.com", true));
        when(jobService.buildDTO(job, "hr@test.com")).thenReturn(
                new JobExpiredEventDTO(job.getId().toString(), "alice", "hr@test.com", "Java Dev", "FPT"));
        OutboxExpiredJobEvent statusEvent = new OutboxExpiredJobEvent();
        OutboxExpiredJobEvent mailEvent = new OutboxExpiredJobEvent();
        when(outboxService.buildEvent(eq("JOB_EXPIRED"), any(), anyString())).thenReturn(statusEvent);
        when(outboxService.buildEvent(eq("SEND_MAIL"), any(), anyString())).thenReturn(mailEvent);

        scheduler.closeExpiredJobs();

        assertThat(job.getStatus()).isEqualTo(JobStatus.CLOSED);
        ArgumentCaptor<List<OutboxExpiredJobEvent>> eventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(jobService).updateJobsAndEvents(eq(List.of(job)), eventsCaptor.capture());
        assertThat(eventsCaptor.getValue()).containsExactly(statusEvent, mailEvent);
    }

    @Test
    void closeExpiredJobs_skipsMailEvent_whenHrDisabledJobExpiryNotifications() {
        Job job = job("alice");
        when(jobService.findExpiredJobsToClose()).thenReturn(List.of(job));
        when(jobService.fetchEmailMap(List.of(job))).thenReturn(Map.of("alice", "hr@test.com"));
        when(jobService.fetchHrJobExpirySettingsMap(any())).thenReturn(Map.of("hr@test.com", false));
        when(jobService.buildDTO(job, "hr@test.com")).thenReturn(
                new JobExpiredEventDTO(job.getId().toString(), "alice", "hr@test.com", "Java Dev", "FPT"));
        OutboxExpiredJobEvent statusEvent = new OutboxExpiredJobEvent();
        when(outboxService.buildEvent(eq("JOB_EXPIRED"), any(), anyString())).thenReturn(statusEvent);

        scheduler.closeExpiredJobs();

        verify(outboxService, times(1)).buildEvent(eq("JOB_EXPIRED"), any(), anyString());
        verify(outboxService, never()).buildEvent(eq("SEND_MAIL"), any(), anyString());
    }

    @Test
    void closeExpiredJobs_sendsMailEvent_whenHrEmailUnknown() {
        Job job = job("system");
        when(jobService.findExpiredJobsToClose()).thenReturn(List.of(job));
        when(jobService.fetchEmailMap(List.of(job))).thenReturn(Map.of());
        when(jobService.fetchHrJobExpirySettingsMap(any())).thenReturn(Map.of());
        when(jobService.buildDTO(job, null)).thenReturn(
                new JobExpiredEventDTO(job.getId().toString(), "system", null, "Java Dev", "FPT"));
        when(outboxService.buildEvent(any(), any(), anyString())).thenReturn(new OutboxExpiredJobEvent());

        scheduler.closeExpiredJobs();

        verify(outboxService, times(1)).buildEvent(eq("JOB_EXPIRED"), any(), anyString());
        verify(outboxService, times(1)).buildEvent(eq("SEND_MAIL"), any(), anyString());
    }
}
