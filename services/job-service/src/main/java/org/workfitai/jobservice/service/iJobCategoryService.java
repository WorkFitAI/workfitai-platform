package org.workfitai.jobservice.service;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.workfitai.jobservice.model.JobCategory;
import org.workfitai.jobservice.model.dto.request.JobCategory.ReqCreateJobCategoryDTO;
import org.workfitai.jobservice.model.dto.request.JobCategory.ReqUpdateJobCategoryDTO;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.model.dto.response.JobCategory.ResJobCategoryDTO;

public interface iJobCategoryService {
  ResJobCategoryDTO getById(UUID id);

  ResultPaginationDTO fetchAll(Specification<JobCategory> spec, Pageable pageable);

  ResJobCategoryDTO create(ReqCreateJobCategoryDTO dto);

  ResJobCategoryDTO update(ReqUpdateJobCategoryDTO dto);

  void delete(UUID id);
}
