package org.workfitai.jobservice.model.mapper;

import org.springframework.stereotype.Component;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.document.JobDocument;

@Component
public class JobDocumentMapper {

  public JobDocument toDocument(Job job) {

    return JobDocument.builder()
        .jobId(job.getJobId())
        .title(job.getTitle())
        .description(job.getDescription())
        .shortDescription(job.getShortDescription())

        .companyNo(job.getCompany().getCompanyNo())
        .companyName(job.getCompany().getName())

        .skills(
            job.getSkills()
                .stream()
                .map(Skill::getName)
                .toList())

        .categoryId(job.getJobCategory().getId())
        .categoryName(job.getJobCategory().getName())

        .employmentType(job.getEmploymentType().name())
        .experienceLevel(job.getExperienceLevel().name())
        .status(job.getStatus().name())

        .salaryMin(job.getSalaryMin())
        .salaryMax(job.getSalaryMax())
        .currency(job.getCurrency())

        .location(job.getLocation())
        .benefits(job.getBenefits())
        .requirements(job.getRequirements())
        .responsibilities(job.getResponsibilities())

        .views(job.getViews())
        .createdDate(job.getCreatedDate())
        .expiresAt(job.getExpiresAt())

        .deleted(job.isDeleted())

        .build();
  }
}