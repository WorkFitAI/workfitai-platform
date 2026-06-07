package org.workfitai.jobservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.workfitai.jobservice.config.errors.InvalidDataException;
import org.workfitai.jobservice.config.errors.ResourceConflictException;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.request.Skill.ReqCreateSkillDTO;
import org.workfitai.jobservice.model.dto.request.Skill.ReqUpdateSkillDTO;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.model.dto.response.Skill.ResSkillDTO;
import org.workfitai.jobservice.model.dto.response.Skill.ResTopSkillDTO;
import org.workfitai.jobservice.model.dto.response.Skill.ResUpdateSkillDTO;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.model.mapper.SkillMapper;
import org.workfitai.jobservice.repository.SkillRepository;
import org.workfitai.jobservice.service.iSkillService;
import org.workfitai.jobservice.util.PaginationUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SkillService implements iSkillService {

    private final SkillRepository skillRepository;
    private final SkillMapper skillMapper;

    @Override
    public ResSkillDTO getById(UUID id) {
        Skill skill = skillRepository.findById(id)
                .orElseThrow(() -> new InvalidDataException("Skill not found"));
        return skillMapper.toResDTO(skill);
    }

    @Override
    public ResultPaginationDTO fetchAll(Specification<Skill> spec, Pageable pageable) {
        Page<Skill> page = skillRepository.findAll(spec, pageable);
        return PaginationUtils.toResultPaginationDTO(page, skillMapper::toResDTO);
    }

    @Override
    public ResSkillDTO create(ReqCreateSkillDTO dto) {
        skillRepository.findByName(dto.getName())
                .ifPresent(s -> {
                    throw new ResourceConflictException("Skill with the same name already exists");
                });
        Skill skill = skillMapper.toEntity(dto);
        skillRepository.save(skill);
        return skillMapper.toResDTO(skill);
    }

    @Override
    public ResUpdateSkillDTO update(ReqUpdateSkillDTO dto) {
        Skill skill = skillRepository.findById(dto.getSkillId())
                .orElseThrow(() -> new InvalidDataException("Skill not found"));

        skillRepository.findByName(dto.getName())
                .filter(s -> !s.getId().equals(skill.getId()))
                .ifPresent(s -> {
                    throw new ResourceConflictException(
                            "Skill with the same name already exists");
                });

        skillMapper.updateEntityFromDTO(dto, skill);
        skillRepository.save(skill);
        return skillMapper.toResUpdateDTO(skill);
    }

    @Override
    public void delete(UUID id) {
        if (!skillRepository.existsById(id)) {
            throw new InvalidDataException("Skill not found");
        }
        skillRepository.deleteById(id);
    }

    @Override
    public List<ResTopSkillDTO> getTopSkillsByDemand(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return skillRepository.findTopSkillsByPublishedJobCount(JobStatus.PUBLISHED, pageable)
                .stream()
                .map(p -> new ResTopSkillDTO(p.getSkillId(), p.getSkillName(), p.getJobCount()))
                .toList();
    }
}
