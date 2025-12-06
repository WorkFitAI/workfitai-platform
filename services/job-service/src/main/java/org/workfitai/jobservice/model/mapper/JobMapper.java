package org.workfitai.jobservice.model.mapper;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.request.ReqJobDTO;
import org.workfitai.jobservice.model.dto.request.ReqUpdateJobDTO;
import org.workfitai.jobservice.model.dto.response.*;
import org.workfitai.jobservice.repository.CompanyRepository;
import org.workfitai.jobservice.repository.SkillRepository;

import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface JobMapper {
    JobMapper INSTANCE = Mappers.getMapper(JobMapper.class);

    @Mapping(target = "company", source = "companyNo")
    @Mapping(target = "skills", source = "skillIds")
    Job toEntity(ReqJobDTO jobDTO, @Context CompanyRepository companyRepo, @Context SkillRepository skillRepo);

    @Mapping(target = "company", source = "companyNo")
    @Mapping(target = "skills", source = "skillIds")
    @Mapping(target = "totalApplications", ignore = true)
    Job toEntity(ReqUpdateJobDTO jobDTO, @Context CompanyRepository companyRepo, @Context SkillRepository skillRepo);

    @Mapping(target = "postId", source = "jobId")
    ResCreateJobDTO toResCreateJobDTO(Job job);

    @Mapping(target = "skillNames", source = "skills")
    @Mapping(target = "postId", source = "jobId")
    ResJobDetailsDTO toResJobDetailsDTO(Job job);

    @Mapping(target = "skillNames", source = "skills")
    @Mapping(target = "postId", source = "jobId")
    ResJobDetailsForHrDTO toResJobDetailsForHrDTO(Job job);

    @Mapping(target = "postId", source = "jobId")
    ResJobDTO toResJobDTO(Job job);

    @Mapping(target = "skillNames", source = "skills")
    @Mapping(target = "postId", source = "jobId")
    ResUpdateJobDTO toResUpdateJobDTO(Job job);

    default Company mapCompany(String companyNo, @Context CompanyRepository companyRepo) {
        if (companyNo == null) return null;
        return companyRepo.findById(companyNo)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyNo));
    }

    default List<Skill> mapSkills(List<UUID> skillIds, @Context SkillRepository skillRepo) {
        if (skillIds == null) return null;
        return skillRepo.findAllById(skillIds);
    }

    // List<Skill> -> List<String>
    default List<String> mapSkillsToNames(List<Skill> skills) {
        if (skills == null) return null;
        return skills.stream().map(Skill::getName).toList();
    }
}
