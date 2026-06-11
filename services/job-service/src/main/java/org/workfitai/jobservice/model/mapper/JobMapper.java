package org.workfitai.jobservice.model.mapper;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.JobCategory;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.request.Job.ReqJobDTO;
import org.workfitai.jobservice.model.dto.request.Job.ReqUpdateJobDTO;
import org.workfitai.jobservice.model.dto.response.Job.*;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.repository.CompanyRepository;
import org.workfitai.jobservice.repository.JobCategoryRepository;
import org.workfitai.jobservice.repository.SkillRepository;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface JobMapper {
    JobMapper INSTANCE = Mappers.getMapper(JobMapper.class);

    @Mapping(target = "company", source = "companyNo")
    @Mapping(target = "skills", source = "skillIds")
    @Mapping(target = "jobCategory", source = "jobCategoryId")
    Job toEntity(ReqJobDTO jobDTO, @Context CompanyRepository companyRepo, @Context SkillRepository skillRepo,
            @Context JobCategoryRepository jobCategoryRepo);

    @Mapping(target = "company", source = "companyNo")
    @Mapping(target = "skills", source = "skillIds")
    @Mapping(target = "totalApplications", ignore = true)
    @Mapping(target = "jobCategory", source = "jobCategoryId")
    Job toEntity(ReqUpdateJobDTO jobDTO, @Context CompanyRepository companyRepo, @Context SkillRepository skillRepo,
            @Context JobCategoryRepository jobCategoryRepo);

    @Mapping(target = "postId", source = "jobId")
    ResCreateJobDTO toResCreateJobDTO(Job job);

    @Mapping(target = "skillNames", source = "skills")
    @Mapping(target = "postId", source = "jobId")
    @Mapping(target = "status", expression = "java(mapStatus(job))")
    @Mapping(target = "jobCategoryName", source = "jobCategory")
    ResJobDetailsDTO toResJobDetailsDTO(Job job);

    @Mapping(target = "skillNames", source = "skills")
    @Mapping(target = "postId", source = "jobId")
    @Mapping(target = "jobCategoryName", source = "jobCategory")
    ResJobDetailsForHrDTO toResJobDetailsForHrDTO(Job job);

    @Mapping(target = "skillNames", source = "skills")
    @Mapping(target = "postId", source = "jobId")
    @Mapping(target = "jobCategoryName", source = "jobCategory")
    ResJobDetailsForAdminDTO toResJobDetailsForAdminDTO(Job job);

    @Mapping(target = "skillNames", source = "skills")
    @Mapping(target = "postId", source = "jobId")
    @Mapping(target = "status", expression = "java(mapStatus(job))")
    @Mapping(target = "jobCategoryName", source = "jobCategory")
    ResJobDTO toResJobDTO(Job job);

    @Mapping(target = "skillNames", source = "skills")
    @Mapping(target = "postId", source = "jobId")
    @Mapping(target = "jobCategoryName", source = "jobCategory")
    ResJobDTOForManagement toResJobDTOForManagement(Job job);

    @Mapping(target = "skillNames", source = "skills")
    @Mapping(target = "postId", source = "jobId")
    @Mapping(target = "jobCategoryName", source = "jobCategory")
    ResUpdateJobDTO toResUpdateJobDTO(Job job);

    default Company mapCompany(String companyNo, @Context CompanyRepository companyRepo) {
        if (companyNo == null)
            return null;
        return companyRepo.findById(companyNo)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyNo));
    }

    default List<Skill> mapSkills(List<UUID> skillIds, @Context SkillRepository skillRepo) {
        if (skillIds == null)
            return null;
        return skillRepo.findAllById(skillIds);
    }

    // List<Skill> -> List<String>
    default List<String> mapSkillsToNames(List<Skill> skills) {
        if (skills == null)
            return null;
        return skills.stream().map(Skill::getName).toList();
    }

    default JobStatus mapStatus(Job job) {
        return job.getEffectiveStatus();
    }

    default String mapJobCategoryName(JobCategory category) {
        return category != null ? category.getName() : null;
    }

    default JobCategory map(
            UUID jobCategoryId,
            @Context JobCategoryRepository jobCategoryRepo) {

        if (jobCategoryId == null) {
            return null;
        }

        return jobCategoryRepo.findById(jobCategoryId)
                .orElseThrow(() -> new RuntimeException("Job category not found"));
    }
}
