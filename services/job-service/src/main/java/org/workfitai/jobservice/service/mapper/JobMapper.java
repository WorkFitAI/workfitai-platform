package org.workfitai.jobservice.service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.workfitai.jobservice.domain.Job;
import org.workfitai.jobservice.domain.Skill;
import org.workfitai.jobservice.service.dto.response.ResCreateJobDTO;
import org.workfitai.jobservice.service.dto.response.ResJobDTO;
import org.workfitai.jobservice.service.dto.response.ResUpdateJobDTO;

import java.util.List;

@Mapper(componentModel = "spring")
public interface JobMapper {
    JobMapper INSTANCE = Mappers.getMapper(JobMapper.class);

    Job toEntity(ResCreateJobDTO resCreateJobDTO);

    @Mapping(target = "skillNames", source = "skills")
    @Mapping(target = "postId", source = "jobId")
    ResCreateJobDTO toResCreateJobDTO(Job job);

    @Mapping(target = "skillNames", source = "skills")
    @Mapping(target = "postId", source = "jobId")
    ResJobDTO toResJobDTO(Job job);

    @Mapping(target = "skillNames", source = "skills")
    @Mapping(target = "postId", source = "jobId")
    ResUpdateJobDTO toResUpdateJobDTO(Job job);

    default List<String> mapSkillsToNames(List<Skill> skills) {
        if (skills == null) return null;
        return skills.stream().map(Skill::getName).toList();
    }
}
