package org.workfitai.jobservice.web.rest;

import com.turkraft.springfilter.boot.Filter;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.workfitai.jobservice.domain.Job;
import org.workfitai.jobservice.domain.response.RestResponse;
import org.workfitai.jobservice.service.JobService;
import org.workfitai.jobservice.service.dto.response.ResCreateJobDTO;
import org.workfitai.jobservice.service.dto.response.ResJobDTO;
import org.workfitai.jobservice.service.dto.response.ResUpdateJobDTO;
import org.workfitai.jobservice.service.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.util.ApiMessage;
import org.workfitai.jobservice.web.errors.InvalidDataException;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping()
@Transactional
public class JobApi {
    private final JobService jobService;

    public JobApi(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping("")
    @ApiMessage("Create new job")
    public ResponseEntity<RestResponse<ResCreateJobDTO>> create(@Valid @RequestBody Job job) {
        RestResponse<ResCreateJobDTO> response = new RestResponse<>();
        response.setStatusCode(HttpStatus.CREATED.value());
        response.setError(null);
        response.setMessage("Job created successfully");
        response.setData(this.jobService.createJob(job));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @ApiMessage("Get a job by id")
    public ResponseEntity<RestResponse<ResJobDTO>> getJob(@PathVariable("id") UUID id) throws InvalidDataException {
        ResJobDTO currentJob = this.jobService.fetchJobById(id);
        if (currentJob == null) {
            throw new InvalidDataException("Job not found");
        }
        RestResponse<ResJobDTO> response = new RestResponse<>();
        response.setStatusCode(HttpStatus.OK.value());
        response.setError(null);
        response.setData(currentJob);

        return ResponseEntity.ok().body(response);
    }

    @GetMapping("")
    @ApiMessage("Get job with pagination")
    public ResponseEntity<RestResponse<ResultPaginationDTO>> getAllJob(
            @Filter Specification<Job> spec,
            Pageable pageable) {
        RestResponse<ResultPaginationDTO> response = new RestResponse<>();
        response.setStatusCode(HttpStatus.OK.value());
        response.setError(null);
        response.setData(this.jobService.fetchAll(spec, pageable));

        return ResponseEntity.ok().body(response);
    }

    @PutMapping("")
    @ApiMessage("Update a job")
    public ResponseEntity<RestResponse<ResUpdateJobDTO>> update(@Valid @RequestBody Job job) throws InvalidDataException {
        Optional<Job> currentJob = this.jobService.getJobById(job.getId());
        if (currentJob.isEmpty()) {
            throw new InvalidDataException("Job not found");
        }

        RestResponse<ResUpdateJobDTO> response = new RestResponse<>();
        response.setStatusCode(HttpStatus.OK.value());
        response.setError(null);
        response.setData(this.jobService.updateJob(job, currentJob.get()));

        return ResponseEntity.ok().body(response);
    }

    @DeleteMapping("/{id}")
    @ApiMessage("Delete a job by id")
    public ResponseEntity<RestResponse<Void>> delete(@PathVariable("id") UUID id) throws InvalidDataException {
        try {
            this.jobService.deleteJob(id);
        } catch (ResourceNotFoundException ex) {
            throw new InvalidDataException("Job not found");
        }

        return ResponseEntity.ok().body(null);
    }
}
