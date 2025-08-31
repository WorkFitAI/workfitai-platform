package org.workfitai.jobservice.controller;

import com.turkraft.springfilter.boot.Filter;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.workfitai.jobservice.config.errors.InvalidDataException;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.dto.request.ReqJobDTO;
import org.workfitai.jobservice.model.dto.request.ReqUpdateJobDTO;
import org.workfitai.jobservice.model.dto.response.*;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.service.iJobService;
import org.workfitai.jobservice.util.ApiMessage;

import java.util.Optional;
import java.util.UUID;

import static org.workfitai.jobservice.util.MessageConstant.*;

@RestController
@RequestMapping()
@Transactional
public class JobApi {
    private final iJobService jobService;

    public JobApi(iJobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping()
    @ApiMessage(JOB_CREATED_SUCCESSFULLY)
    public RestResponse<ResCreateJobDTO> create(@Valid @RequestBody ReqJobDTO jobDTO) {
        return RestResponse.created(jobService.createJob(jobDTO));
    }

    @GetMapping("/{id}")
    @ApiMessage(JOB_DETAIL_FETCHED_SUCCESSFULLY)
    public RestResponse<ResJobDTO> getJob(@PathVariable("id") UUID id) throws InvalidDataException {
        ResJobDTO currentJob = this.jobService.fetchJobById(id);
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


    @PutMapping()
    @ApiMessage(JOB_UPDATED_SUCCESSFULLY)
    public RestResponse<ResUpdateJobDTO> update(@Valid @RequestBody ReqUpdateJobDTO jobDTO) throws InvalidDataException {
        Optional<Job> currentJob = this.jobService.getJobById(jobDTO.getJobId());
        if (currentJob.isEmpty()) {
            throw new InvalidDataException(JOB_NOT_FOUND);
        }

        return RestResponse.success(this.jobService.updateJob(jobDTO, currentJob.get()));
    }

    @PutMapping("/{id}/{status}")
    @ApiMessage(JOB_STATUS_UPDATED_SUCCESSFULLY)
    public RestResponse<ResModifyStatus> updateStatus(
            @PathVariable("id") UUID id, @PathVariable("status") JobStatus status) throws InvalidDataException {
        Optional<Job> currentJob = this.jobService.getJobById(id);
        if (currentJob.isEmpty()) {
            throw new InvalidDataException(JOB_NOT_FOUND);
        }

        return RestResponse.success(this.jobService.updateStatus(currentJob.get(), status));
    }

    @DeleteMapping("/{id}")
    @ApiMessage(JOB_DELETED_SUCCESSFULLY)
    public RestResponse<Void> delete(@PathVariable("id") UUID id) throws InvalidDataException {
        try {
            this.jobService.deleteJob(id);
        } catch (ResourceNotFoundException ex) {
            throw new InvalidDataException(JOB_NOT_FOUND);
        }

        return RestResponse.deleted();
    }
}
