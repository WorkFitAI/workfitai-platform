package org.workfitai.jobservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.kafka.JobCreatedEvent;
import org.workfitai.jobservice.model.dto.kafka.JobDeletedEvent;
import org.workfitai.jobservice.model.dto.kafka.JobUpdatedEvent;
import org.workfitai.jobservice.model.enums.EmploymentType;
import org.workfitai.jobservice.model.enums.ExperienceLevel;
import org.workfitai.jobservice.model.enums.JobStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobEventProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks
    private JobEventProducer jobEventProducer;

    private Job job(UUID jobId) {
        Job job = new Job();
        job.setJobId(jobId);
        job.setTitle("Java Developer");
        job.setStatus(JobStatus.PUBLISHED);
        job.setCompany(Company.builder().companyNo("C-A").name("FPT").build());
        job.setSkills(List.of(new Skill("Java")));
        return job;
    }

    @Test
    void publishJobCreated_sendsEventToKafka() {
        UUID jobId = UUID.randomUUID();
        Job job = job(jobId);

        jobEventProducer.publishJobCreated(job);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(any(), org.mockito.ArgumentMatchers.eq(jobId.toString()), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(JobCreatedEvent.class);
        assertThat(((JobCreatedEvent) eventCaptor.getValue()).getEventType()).isEqualTo("JOB_CREATED");
    }

    @Test
    void publishJobCreated_neverThrows_whenKafkaSendFails() {
        doThrow(new RuntimeException("kafka down")).when(kafkaTemplate).send(any(), anyString(), any());

        jobEventProducer.publishJobCreated(job(UUID.randomUUID()));
    }

    @Test
    void publishJobUpdated_withoutChanges_sendsEventWithNullChanges() {
        UUID jobId = UUID.randomUUID();

        jobEventProducer.publishJobUpdated(job(jobId));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(any(), org.mockito.ArgumentMatchers.eq(jobId.toString()), eventCaptor.capture());
        assertThat(((JobUpdatedEvent) eventCaptor.getValue()).getChanges()).isNull();
    }

    @Test
    void publishJobUpdated_withChanges_sendsEventWithChanges() {
        UUID jobId = UUID.randomUUID();
        Map<String, Object> changes = Map.of("title", Map.of("old", "A", "new", "B"));

        jobEventProducer.publishJobUpdated(job(jobId), changes);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(any(), org.mockito.ArgumentMatchers.eq(jobId.toString()), eventCaptor.capture());
        assertThat(((JobUpdatedEvent) eventCaptor.getValue()).getChanges()).isEqualTo(changes);
    }

    @Test
    void publishJobExpired_sendsEventWithStatusChange() {
        UUID jobId = UUID.randomUUID();

        jobEventProducer.publishJobExpired(job(jobId));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(any(), org.mockito.ArgumentMatchers.eq(jobId.toString()), eventCaptor.capture());
        JobUpdatedEvent event = (JobUpdatedEvent) eventCaptor.getValue();
        assertThat(event.getEventType()).isEqualTo("JOB_EXPIRED");
        assertThat(event.getChanges()).containsKey("status");
    }

    @Test
    void publishJobDeleted_sendsEventWithReasonAndStatus() {
        UUID jobId = UUID.randomUUID();
        Job job = job(jobId);
        job.setStatus(JobStatus.CLOSED);

        jobEventProducer.publishJobDeleted(job, "violates policy");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(any(), org.mockito.ArgumentMatchers.eq(jobId.toString()), eventCaptor.capture());
        JobDeletedEvent event = (JobDeletedEvent) eventCaptor.getValue();
        assertThat(event.getReason()).isEqualTo("violates policy");
        assertThat(event.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void detectChanges_returnsNull_whenNoFieldsDiffer() {
        Job oldJob = job(UUID.randomUUID());
        Job newJob = job(UUID.randomUUID());
        newJob.setTitle(oldJob.getTitle());
        newJob.setSalaryMax(oldJob.getSalaryMax());

        assertThat(jobEventProducer.detectChanges(oldJob, newJob)).isNull();
    }

    @Test
    void detectChanges_returnsChangedFields() {
        Job oldJob = job(UUID.randomUUID());
        oldJob.setTitle("Old Title");
        Job newJob = job(UUID.randomUUID());
        newJob.setTitle("New Title");

        Map<String, Object> changes = jobEventProducer.detectChanges(oldJob, newJob);

        assertThat(changes).containsKey("title");
    }

    @Test
    void detectChanges_returnsDescriptionChange_whenDescriptionDiffers() {
        Job oldJob = job(UUID.randomUUID());
        oldJob.setDescription("Old description");
        Job newJob = job(UUID.randomUUID());
        newJob.setTitle(oldJob.getTitle());
        newJob.setDescription("New description");

        Map<String, Object> changes = jobEventProducer.detectChanges(oldJob, newJob);

        assertThat(changes).containsKey("description");
    }

    @Test
    void detectChanges_returnsSalaryMaxChange_whenSalaryMaxDiffers() {
        Job oldJob = job(UUID.randomUUID());
        oldJob.setSalaryMax(BigDecimal.valueOf(1000));
        Job newJob = job(UUID.randomUUID());
        newJob.setTitle(oldJob.getTitle());
        newJob.setSalaryMax(BigDecimal.valueOf(2000));

        Map<String, Object> changes = jobEventProducer.detectChanges(oldJob, newJob);

        assertThat(changes).containsKey("salaryMax");
    }

    @Test
    void publishJobCreated_mapsAllOptionalFields_whenPresent() {
        UUID jobId = UUID.randomUUID();
        Job job = job(jobId);
        job.setEmploymentType(EmploymentType.FULL_TIME);
        job.setExperienceLevel(ExperienceLevel.JUNIOR);
        job.setSalaryMin(BigDecimal.valueOf(500));
        job.setSalaryMax(BigDecimal.valueOf(1500));

        jobEventProducer.publishJobCreated(job);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(any(), org.mockito.ArgumentMatchers.eq(jobId.toString()), eventCaptor.capture());
        JobCreatedEvent event = (JobCreatedEvent) eventCaptor.getValue();
        assertThat(event.getData().getEmploymentType()).isEqualTo("FULL_TIME");
        assertThat(event.getData().getExperienceLevel()).isEqualTo("JUNIOR");
        assertThat(event.getData().getSalaryMin()).isEqualTo(500.0);
        assertThat(event.getData().getSalaryMax()).isEqualTo(1500.0);
        assertThat(event.getData().getCompany()).isNotNull();
    }

    @Test
    void publishJobCreated_mapsNullCompany_whenCompanyAbsent() {
        UUID jobId = UUID.randomUUID();
        Job job = job(jobId);
        job.setCompany(null);
        job.setSkills(null);

        jobEventProducer.publishJobCreated(job);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(any(), org.mockito.ArgumentMatchers.eq(jobId.toString()), eventCaptor.capture());
        JobCreatedEvent event = (JobCreatedEvent) eventCaptor.getValue();
        assertThat(event.getData().getCompany()).isNull();
        assertThat(event.getData().getSkills()).isNull();
    }
}
