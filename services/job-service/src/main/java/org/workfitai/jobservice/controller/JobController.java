package org.workfitai.jobservice.controller;

import com.turkraft.springfilter.boot.Filter;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.jobservice.config.errors.InvalidDataException;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.dto.response.ResJobDetailsDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.service.iJobService;
import org.workfitai.jobservice.util.ApiMessage;

import java.util.UUID;

import static org.workfitai.jobservice.util.MessageConstant.*;

@RestController
@RequestMapping("/public/jobs")
@Transactional
public class JobController {
    private final iJobService jobService;

    public JobController(iJobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/{id}")
    @ApiMessage(JOB_DETAIL_FETCHED_SUCCESSFULLY)
    public RestResponse<ResJobDetailsDTO> getJob(@PathVariable("id") UUID id) throws InvalidDataException {
        ResJobDetailsDTO currentJob = this.jobService.fetchJobById(id);
        if (currentJob == null) {
            throw new InvalidDataException(JOB_NOT_FOUND);
        }
        return RestResponse.success(currentJob);
    }

    @GetMapping()
    @ApiMessage(JOB_ALL_FETCHED_SUCCESSFULLY)
    public RestResponse<ResultPaginationDTO> getAllJob(
            @Filter Specification<Job> spec,
            Pageable pageable) {

        ResultPaginationDTO result = this.jobService.fetchAll(spec, pageable);
        return RestResponse.success(result);
    }
}
