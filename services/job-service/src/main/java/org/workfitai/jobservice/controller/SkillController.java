package org.workfitai.jobservice.controller;

import com.turkraft.springfilter.boot.Filter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.request.ReqCreateSkillDTO;
import org.workfitai.jobservice.model.dto.request.ReqUpdateSkillDTO;
import org.workfitai.jobservice.model.dto.response.ResSkillDTO;
import org.workfitai.jobservice.model.dto.response.ResUpdateSkillDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.service.iSkillService;
import org.workfitai.jobservice.util.ApiMessage;

import java.util.UUID;

import static org.workfitai.jobservice.util.MessageConstant.*;

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

    @PostMapping()
    @ApiMessage(SKILL_CREATED_SUCCESSFULLY)
    public RestResponse<ResSkillDTO> createSkill(@Valid @RequestBody ReqCreateSkillDTO dto) {
        return RestResponse.success(skillService.create(dto));
    }

    @PutMapping()
    @ApiMessage(SKILL_UPDATED_SUCCESSFULLY)
    public RestResponse<ResUpdateSkillDTO> updateSkill(@Valid @RequestBody ReqUpdateSkillDTO dto) {
        return RestResponse.success(skillService.update(dto));
    }

    @DeleteMapping("/{id}")
    @ApiMessage(SKILL_DELETED_SUCCESSFULLY)
    public RestResponse<Void> deleteSkill(@PathVariable UUID id) {
        skillService.delete(id);
        return RestResponse.success(null);
    }
}
