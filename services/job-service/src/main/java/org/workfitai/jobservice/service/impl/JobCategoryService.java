package org.workfitai.jobservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.workfitai.jobservice.model.JobCategory;
import org.workfitai.jobservice.model.dto.request.JobCategory.ReqCreateJobCategoryDTO;
import org.workfitai.jobservice.model.dto.request.JobCategory.ReqUpdateJobCategoryDTO;
import org.workfitai.jobservice.model.dto.response.JobCategory.ResJobCategoryDTO;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.model.mapper.JobCategoryMapper;
import org.workfitai.jobservice.repository.JobCategoryRepository;
import org.workfitai.jobservice.service.iJobCategoryService;
import org.workfitai.jobservice.util.PaginationUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobCategoryService implements iJobCategoryService {

  private final JobCategoryRepository jobCategoryRepository;
  private final JobCategoryMapper jobCategoryMapper;

  @Override
  public ResJobCategoryDTO getById(UUID id) {

    JobCategory category = jobCategoryRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Job category not found"));

    return jobCategoryMapper.toResDTO(category);
  }

  @Override
  public ResultPaginationDTO fetchAll(Specification<JobCategory> spec, Pageable pageable) {

    Page<JobCategory> page = jobCategoryRepository.findAll(spec, pageable);

    return PaginationUtils.toResultPaginationDTO(
        page,
        jobCategoryMapper::toResDTO);
  }

  @Override
  public ResJobCategoryDTO create(ReqCreateJobCategoryDTO dto) {

    JobCategory category = jobCategoryMapper.toEntity(dto);

    category = jobCategoryRepository.save(category);

    return jobCategoryMapper.toResDTO(category);
  }

  @Override
  public ResJobCategoryDTO update(ReqUpdateJobCategoryDTO dto) {

    JobCategory category = jobCategoryRepository.findById(dto.getId())
        .orElseThrow(() -> new RuntimeException("Job category not found"));

    jobCategoryMapper.updateEntityFromDTO(dto, category);

    category = jobCategoryRepository.save(category);

    return jobCategoryMapper.toResDTO(category);
  }

  @Override
  public void delete(UUID id) {

    if (!jobCategoryRepository.existsById(id)) {
      throw new RuntimeException("Job category not found");
    }

    jobCategoryRepository.deleteById(id);
  }
}