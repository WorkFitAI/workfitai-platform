package org.workfitai.jobservice.model.mapper;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.request.ReqCreateSkillDTO;
import org.workfitai.jobservice.model.dto.request.ReqUpdateSkillDTO;
import org.workfitai.jobservice.model.dto.response.ResSkillDTO;
import org.workfitai.jobservice.model.dto.response.ResUpdateSkillDTO;

@Mapper(componentModel = "spring")
public interface SkillMapper {

    Skill toEntity(ReqCreateSkillDTO dto);

    void updateEntityFromDTO(ReqUpdateSkillDTO dto, @MappingTarget Skill skill);

    ResSkillDTO toResDTO(Skill skill);

    ResUpdateSkillDTO toResUpdateDTO(Skill skill);
}