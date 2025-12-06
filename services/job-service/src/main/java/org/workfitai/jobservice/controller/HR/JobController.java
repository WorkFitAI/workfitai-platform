package org.workfitai.jobservice.controller.HR;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
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

@RestController("hrJobController")
@RequestMapping("/hr/jobs")
@Transactional
public class JobController {
    private final iJobService jobService;

    public JobController(iJobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/{id}")
    @ApiMessage(JOB_DETAIL_FETCHED_SUCCESSFULLY)
    public RestResponse<ResJobDetailsForHrDTO> getJob(@PathVariable("id") UUID id) throws InvalidDataException {
        ResJobDetailsForHrDTO currentJob = this.jobService.fetchJobByIdForHr(id);
        if (currentJob == null) {
            throw new InvalidDataException(JOB_NOT_FOUND);
        }
        return RestResponse.success(currentJob);
    }

    @PostMapping()
    @ApiMessage(JOB_CREATED_SUCCESSFULLY)
    public RestResponse<ResCreateJobDTO> create(@Valid @RequestBody ReqJobDTO jobDTO) {
        return RestResponse.created(jobService.createJob(jobDTO));
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
