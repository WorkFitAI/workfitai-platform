package org.workfitai.jobservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.request.ReqCreateSkillDTO;
import org.workfitai.jobservice.model.dto.request.ReqUpdateSkillDTO;
import org.workfitai.jobservice.model.dto.response.ResSkillDTO;
import org.workfitai.jobservice.model.dto.response.ResUpdateSkillDTO;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.model.mapper.SkillMapper;
import org.workfitai.jobservice.repository.SkillRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillService implements org.workfitai.jobservice.service.iSkillService {

    private final SkillRepository skillRepository;
    private final SkillMapper skillMapper;

    @Override
    public ResSkillDTO getById(UUID id) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Skill not found"));
        return skillMapper.toResDTO(skill);
    }

    @Override
    public ResultPaginationDTO fetchAll(Specification<Skill> spec, Pageable pageable) {
        Page<Skill> page = skillRepository.findAll(spec, pageable);
        Page<ResSkillDTO> pageDTO = page.map(skillMapper::toResDTO);

        ResultPaginationDTO rs = new ResultPaginationDTO();
        ResultPaginationDTO.Meta mt = new ResultPaginationDTO.Meta();
        mt.setPage(pageable.getPageNumber() + 1);
        mt.setPageSize(pageable.getPageSize());
        mt.setPages(page.getTotalPages());
        mt.setTotal(page.getTotalElements());

        rs.setMeta(mt);
        rs.setResult(pageDTO.getContent());
        return rs;
    }

    @Override
    public ResSkillDTO create(ReqCreateSkillDTO dto) {
        Skill skill = skillMapper.toEntity(dto);
        skillRepository.save(skill); // JWT audit sẽ tự fill createdBy
        return skillMapper.toResDTO(skill);
    }

    @Override
    public ResUpdateSkillDTO update(ReqUpdateSkillDTO dto) {
        Skill skill = skillRepository.findById(dto.getSkillId())
                .orElseThrow(() -> new RuntimeException("Skill not found"));

        skillMapper.updateEntityFromDTO(dto, skill);
        skillRepository.save(skill); // JWT audit tự fill lastModifiedBy
        return skillMapper.toResUpdateDTO(skill);
    }

    @Override
    public void delete(UUID id) {
        if (!skillRepository.existsById(id)) {
            throw new RuntimeException("Skill not found");
        }
        skillRepository.deleteById(id);
    }
}
