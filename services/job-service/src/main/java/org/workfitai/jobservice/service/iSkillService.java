package org.workfitai.jobservice.service;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.request.Skill.ReqCreateSkillDTO;
import org.workfitai.jobservice.model.dto.request.Skill.ReqUpdateSkillDTO;
import org.workfitai.jobservice.model.dto.response.Skill.ResSkillDTO;
import org.workfitai.jobservice.model.dto.response.Skill.ResUpdateSkillDTO;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;

import java.util.UUID;

public interface iSkillService {
    ResSkillDTO getById(UUID id);

    ResultPaginationDTO fetchAll(Specification<Skill> spec, Pageable pageable);

    ResSkillDTO create(ReqCreateSkillDTO dto);

    ResUpdateSkillDTO update(ReqUpdateSkillDTO dto);

    void delete(UUID id);
}
