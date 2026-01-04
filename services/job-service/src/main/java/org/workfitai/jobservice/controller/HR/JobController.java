package org.workfitai.jobservice.controller.HR;

import com.turkraft.springfilter.boot.Filter;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.jobservice.config.errors.InvalidDataException;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.dto.request.Job.ReqJobDTO;
import org.workfitai.jobservice.model.dto.request.Job.ReqUpdateJobDTO;
import org.workfitai.jobservice.model.dto.response.Job.ResCreateJobDTO;
import org.workfitai.jobservice.model.dto.response.Job.ResJobDetailsForHrDTO;
import org.workfitai.jobservice.model.dto.response.Job.ResModifyStatus;
import org.workfitai.jobservice.model.dto.response.Job.ResUpdateJobDTO;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.service.iJobService;
import org.workfitai.jobservice.util.ApiMessage;

import java.io.IOException;
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

    @PreAuthorize("hasAuthority('job:list')")
    @GetMapping()
    @ApiMessage(JOB_ALL_FETCHED_SUCCESSFULLY)
    public RestResponse<ResultPaginationDTO> getAllJob(
            @Filter Specification<Job> spec,
            Pageable pageable) {

        ResultPaginationDTO result = this.jobService.fetchAllForHr(spec, pageable);
        return RestResponse.success(result);
    }

    @PreAuthorize("hasAuthority('job:read')")
    @GetMapping("/{id}")
    @ApiMessage(JOB_DETAIL_FETCHED_SUCCESSFULLY)
    public RestResponse<ResJobDetailsForHrDTO> getJob(@PathVariable("id") UUID id) throws InvalidDataException {
        ResJobDetailsForHrDTO currentJob = this.jobService.fetchJobByIdForHr(id);
        if (currentJob == null) {
            throw new InvalidDataException(JOB_NOT_FOUND);
        }
        return RestResponse.success(currentJob);
    }

    @PreAuthorize("hasAuthority('job:create')")
    @PostMapping()
    @ApiMessage(JOB_CREATED_SUCCESSFULLY)
    public RestResponse<ResCreateJobDTO> create(@Valid @RequestBody ReqJobDTO jobDTO) {
        return RestResponse.created(jobService.createJob(jobDTO));
    }

    @PreAuthorize("hasAuthority('job:update')")
    @PutMapping()
    @ApiMessage(JOB_UPDATED_SUCCESSFULLY)
    public RestResponse<ResUpdateJobDTO> update(@Valid @RequestBody ReqUpdateJobDTO jobDTO) throws InvalidDataException {
        Optional<Job> currentJob = this.jobService.getJobById(jobDTO.getJobId());
        if (currentJob.isEmpty()) {
            throw new InvalidDataException(JOB_NOT_FOUND);
        }

        return RestResponse.success(this.jobService.updateJob(jobDTO, currentJob.get()));
    }

    @PreAuthorize("hasAuthority('job:update') or hasAuthority('job:create')")
    @PostMapping("/{jobId}/banner")
    public RestResponse<String> uploadBanner(
            @PathVariable UUID jobId,
            @RequestParam("file") MultipartFile bannerFile) throws InvalidDataException, IOException {

        String bannerUrl = jobService.uploadJobBanner(jobId, bannerFile);
        return RestResponse.success(bannerUrl);
    }

    @PreAuthorize("hasAuthority('job:update') or hasAuthority('job:stats')")
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
}
