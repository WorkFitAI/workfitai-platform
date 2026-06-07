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
import org.workfitai.jobservice.model.dto.response.JobCategory.ResJobCategoryDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.service.iJobCategoryService;
import org.workfitai.jobservice.util.ApiMessage;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/public/jobs/job-categories")
@RequiredArgsConstructor
@Validated
public class JobCategoryController {

  private final iJobCategoryService jobCategoryService;

  @GetMapping("/{id}")
  @ApiMessage("Get job category successfully")
  public RestResponse<ResJobCategoryDTO> getById(
      @PathVariable UUID id) {

    return RestResponse.success(
        jobCategoryService.getById(id));
  }

  @GetMapping
  @ApiMessage("Get all job categories successfully")
  public RestResponse<ResultPaginationDTO> getAll(
      @Filter Specification<JobCategory> spec,
      Pageable pageable) {

    return RestResponse.success(
        jobCategoryService.fetchAll(spec, pageable));
  }

  @PostMapping
  @ApiMessage("Create job category successfully")
  public RestResponse<ResJobCategoryDTO> create(
      @Valid @RequestBody ReqCreateJobCategoryDTO dto) {
    return RestResponse.success(
        jobCategoryService.create(dto));
  }

  @PutMapping
  @ApiMessage("Update job category successfully")
  public RestResponse<ResJobCategoryDTO> update(
      @Valid @RequestBody ReqUpdateJobCategoryDTO dto) {

    return RestResponse.success(
        jobCategoryService.update(dto));
  }

  @DeleteMapping("/{id}")
  @ApiMessage("Delete job category successfully")
  public RestResponse<Void> delete(
      @PathVariable UUID id) {

    jobCategoryService.delete(id);

    return RestResponse.success(null);
  }
}