package org.workfitai.jobservice.controller;

import com.turkraft.springfilter.boot.Filter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.model.dto.response.Skill.ResSkillDTO;
import org.workfitai.jobservice.service.iSkillService;
import org.workfitai.jobservice.util.ApiMessage;

import java.util.UUID;

import static org.workfitai.jobservice.util.MessageConstant.SKILL_ALL_FETCHED_SUCCESSFULLY;
import static org.workfitai.jobservice.util.MessageConstant.SKILL_DETAIL_FETCHED_SUCCESSFULLY;

@RestController
@RequestMapping("/public/skills")
@RequiredArgsConstructor
@Validated
public class SkillController {

    private final iSkillService skillService;

    @GetMapping("/{id}")
    @ApiMessage(SKILL_DETAIL_FETCHED_SUCCESSFULLY)
    public RestResponse<ResSkillDTO> getSkill(@PathVariable UUID id) {
        return RestResponse.success(skillService.getById(id));
    }

    @GetMapping()
    @ApiMessage(SKILL_ALL_FETCHED_SUCCESSFULLY)
    public RestResponse<ResultPaginationDTO> getAllSkills(@Filter Specification<Skill> spec, Pageable pageable) {
        return RestResponse.success(skillService.fetchAll(spec, pageable));
    }


}
