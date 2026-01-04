package org.workfitai.jobservice.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.request.Skill.ReqCreateSkillDTO;
import org.workfitai.jobservice.model.dto.request.Skill.ReqUpdateSkillDTO;
import org.workfitai.jobservice.model.dto.response.Skill.ResSkillDTO;
import org.workfitai.jobservice.model.dto.response.Skill.ResUpdateSkillDTO;

@Mapper(componentModel = "spring")
public interface SkillMapper {

    Skill toEntity(ReqCreateSkillDTO dto);

    void updateEntityFromDTO(ReqUpdateSkillDTO dto, @MappingTarget Skill skill);

    ResSkillDTO toResDTO(Skill skill);

    ResUpdateSkillDTO toResUpdateDTO(Skill skill);
}