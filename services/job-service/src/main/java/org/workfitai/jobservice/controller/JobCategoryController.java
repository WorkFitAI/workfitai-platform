package org.workfitai.jobservice.controller;

import com.turkraft.springfilter.boot.Filter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.workfitai.jobservice.model.JobCategory;
import org.workfitai.jobservice.model.dto.request.JobCategory.ReqCreateJobCategoryDTO;
import org.workfitai.jobservice.model.dto.request.JobCategory.ReqUpdateJobCategoryDTO;
import org.workfitai.jobservice.model.dto.response.JobCategory.JobCategoryStatisticDTO;
import org.workfitai.jobservice.model.dto.response.JobCategory.ResJobCategoryDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.service.iJobCategoryService;
import org.workfitai.jobservice.util.ApiMessage;

import jakarta.validation.Valid;

import static org.workfitai.jobservice.util.MessageConstant.JOB_CATEGORY_ALL_FETCHED_SUCCESSFULLY;
import static org.workfitai.jobservice.util.MessageConstant.JOB_CATEGORY_CREATED_SUCCESSFULLY;
import static org.workfitai.jobservice.util.MessageConstant.JOB_CATEGORY_DELETED_SUCCESSFULLY;
import static org.workfitai.jobservice.util.MessageConstant.JOB_CATEGORY_FETCHED_SUCCESSFULLY;
import static org.workfitai.jobservice.util.MessageConstant.JOB_CATEGORY_STATISTICS_FETCHED_SUCCESSFULLY;
import static org.workfitai.jobservice.util.MessageConstant.JOB_CATEGORY_UPDATED_SUCCESSFULLY;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/public/jobs/job-categories")
@RequiredArgsConstructor
@Validated
public class JobCategoryController {

  private final iJobCategoryService jobCategoryService;

  @GetMapping("/{id}")
  @ApiMessage(JOB_CATEGORY_FETCHED_SUCCESSFULLY)
  public RestResponse<ResJobCategoryDTO> getById(
      @PathVariable UUID id) {

    return RestResponse.success(
        jobCategoryService.getById(id));
  }

  @GetMapping
  @ApiMessage(JOB_CATEGORY_ALL_FETCHED_SUCCESSFULLY)
  public RestResponse<ResultPaginationDTO> getAll(
      @Filter Specification<JobCategory> spec,
      Pageable pageable) {

    return RestResponse.success(
        jobCategoryService.fetchAll(spec, pageable));
  }

  @GetMapping("/statistics/top")
  @ApiMessage(JOB_CATEGORY_STATISTICS_FETCHED_SUCCESSFULLY)
  public RestResponse<List<JobCategoryStatisticDTO>> getTopJobCategories(
      @RequestParam(defaultValue = "10") int topN) {

    return RestResponse.success(
        jobCategoryService.getTopJobCategories(topN));
  }

  @PostMapping
  @ApiMessage(JOB_CATEGORY_CREATED_SUCCESSFULLY)
  public RestResponse<ResJobCategoryDTO> create(
      @Valid @RequestBody ReqCreateJobCategoryDTO dto) {
    return RestResponse.success(
        jobCategoryService.create(dto));
  }

  @PutMapping
  @ApiMessage(JOB_CATEGORY_UPDATED_SUCCESSFULLY)
  public RestResponse<ResJobCategoryDTO> update(
      @Valid @RequestBody ReqUpdateJobCategoryDTO dto) {

    return RestResponse.success(
        jobCategoryService.update(dto));
  }

  @DeleteMapping("/{id}")
  @ApiMessage(JOB_CATEGORY_DELETED_SUCCESSFULLY)
  public RestResponse<Void> delete(
      @PathVariable UUID id) {

    jobCategoryService.delete(id);

    return RestResponse.success(null);
  }
}