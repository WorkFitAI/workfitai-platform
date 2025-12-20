package org.workfitai.jobservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.kafka.JobCreatedEvent;
import org.workfitai.jobservice.model.dto.kafka.JobDeletedEvent;
import org.workfitai.jobservice.model.dto.kafka.JobEventData;
import org.workfitai.jobservice.model.dto.kafka.JobUpdatedEvent;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service để publish job events lên Kafka cho Recommendation Engine
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JobEventProducer {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${app.kafka.topics.job-created:job.created}")
    private String jobCreatedTopic;
    
    @Value("${app.kafka.topics.job-updated:job.updated}")
    private String jobUpdatedTopic;
    
    @Value("${app.kafka.topics.job-deleted:job.deleted}")
    private String jobDeletedTopic;
    
    /**
     * Publish event khi tạo job mới
     */
    public void publishJobCreated(Job job) {
        try {
            JobCreatedEvent event = JobCreatedEvent.builder()
                    .eventType("JOB_CREATED")
                    .timestamp(Instant.now())
                    .jobId(job.getJobId())
                    .data(mapJobToEventData(job))
                    .build();
            
            kafkaTemplate.send(jobCreatedTopic, job.getJobId().toString(), event);
            log.info("Published JOB_CREATED event for jobId: {}", job.getJobId());
            
        } catch (Exception e) {
            log.error("Failed to publish JOB_CREATED event for jobId: {}", job.getJobId(), e);
            // Don't throw exception - event publishing shouldn't break the main flow
        }
    }
    
    /**
     * Publish event khi update job
     */
    public void publishJobUpdated(Job job) {
        publishJobUpdated(job, null);
    }
    
    /**
     * Publish event khi update job với tracking changes
     */
    public void publishJobUpdated(Job job, Map<String, Object> changes) {
        try {
            JobUpdatedEvent event = JobUpdatedEvent.builder()
                    .eventType("JOB_UPDATED")
                    .timestamp(Instant.now())
                    .jobId(job.getJobId())
                    .data(mapJobToEventData(job))
                    .changes(changes)
                    .build();
            
            kafkaTemplate.send(jobUpdatedTopic, job.getJobId().toString(), event);
            log.info("Published JOB_UPDATED event for jobId: {}", job.getJobId());
            
        } catch (Exception e) {
            log.error("Failed to publish JOB_UPDATED event for jobId: {}", job.getJobId(), e);
        }
    }
    
    /**
     * Publish event khi xóa/đóng job
     */
    public void publishJobDeleted(Job job, String reason) {
        try {
            JobDeletedEvent event = JobDeletedEvent.builder()
                    .eventType("JOB_DELETED")
                    .timestamp(Instant.now())
                    .jobId(job.getJobId())
                    .reason(reason)
                    .status(job.getStatus().name())
                    .build();
            
            kafkaTemplate.send(jobDeletedTopic, job.getJobId().toString(), event);
            log.info("Published JOB_DELETED event for jobId: {} with reason: {}", job.getJobId(), reason);
            
        } catch (Exception e) {
            log.error("Failed to publish JOB_DELETED event for jobId: {}", job.getJobId(), e);
        }
    }
    
    /**
     * Map Job entity sang JobEventData
     */
    private JobEventData mapJobToEventData(Job job) {
        return JobEventData.builder()
                .jobId(job.getJobId())
                .title(job.getTitle())
                .description(job.getDescription())
                .shortDescription(job.getShortDescription())
                .requirements(job.getRequirements())
                .responsibilities(job.getResponsibilities())
                .benefits(job.getBenefits())
                .location(job.getLocation())
                .employmentType(job.getEmploymentType() != null ? job.getEmploymentType().name() : null)
                .experienceLevel(job.getExperienceLevel() != null ? job.getExperienceLevel().name() : null)
                .requiredExperience(job.getRequiredExperience())
                .educationLevel(job.getEducationLevel())
                .salaryMin(job.getSalaryMin() != null ? job.getSalaryMin().doubleValue() : null)
                .salaryMax(job.getSalaryMax() != null ? job.getSalaryMax().doubleValue() : null)
                .currency(job.getCurrency())
                .skills(job.getSkills() != null ? 
                        job.getSkills().stream()
                                .map(Skill::getName)
                                .collect(Collectors.toList()) : 
                        null)
                .company(mapCompanyData(job))
                .status(job.getStatus() != null ? job.getStatus().name() : null)
                .expiresAt(job.getExpiresAt())
                .createdAt(job.getCreatedDate())
                .updatedAt(job.getLastModifiedDate())
                .build();
    }
    
    /**
     * Map company data
     */
    private JobEventData.CompanyData mapCompanyData(Job job) {
        if (job.getCompany() == null) {
            return null;
        }
        
        return JobEventData.CompanyData.builder()
                .companyId(job.getCompany().getCompanyNo())
                .companyName(job.getCompany().getName())
                .industry(null)  // Not available in current Company model
                .companySize(job.getCompany().getSize())
                .description(job.getCompany().getDescription())
                .build();
    }
    
    /**
     * Detect changes giữa old và new job (helper method for tracking changes)
     */
    public Map<String, Object> detectChanges(Job oldJob, Job newJob) {
        Map<String, Object> changes = new HashMap<>();
        
        if (!oldJob.getTitle().equals(newJob.getTitle())) {
            changes.put("title", Map.of("old", oldJob.getTitle(), "new", newJob.getTitle()));
        }
        
        if (oldJob.getDescription() != null && !oldJob.getDescription().equals(newJob.getDescription())) {
            changes.put("description", Map.of("old", oldJob.getDescription(), "new", newJob.getDescription()));
        }
        
        if (oldJob.getSalaryMax() != null && !oldJob.getSalaryMax().equals(newJob.getSalaryMax())) {
            changes.put("salaryMax", Map.of("old", oldJob.getSalaryMax(), "new", newJob.getSalaryMax()));
        }
        
        // Add more fields as needed
        
        return changes.isEmpty() ? null : changes;
    }
}
