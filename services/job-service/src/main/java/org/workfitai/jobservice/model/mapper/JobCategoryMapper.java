package org.workfitai.jobservice.model.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.workfitai.jobservice.model.JobCategory;
import org.workfitai.jobservice.model.dto.request.JobCategory.ReqCreateJobCategoryDTO;
import org.workfitai.jobservice.model.dto.request.JobCategory.ReqUpdateJobCategoryDTO;
import org.workfitai.jobservice.model.dto.response.JobCategory.ResJobCategoryDTO;

@Mapper(componentModel = "spring")
public interface JobCategoryMapper {

  JobCategory toEntity(
      ReqCreateJobCategoryDTO dto);

  ResJobCategoryDTO toResDTO(
      JobCategory entity);

  void updateEntityFromDTO(
      ReqUpdateJobCategoryDTO dto,
      @MappingTarget JobCategory entity);
}