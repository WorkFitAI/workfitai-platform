package org.workfitai.jobservice.controller.Admin;

import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.jobservice.config.errors.InvalidDataException;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.service.ElasticJobService;
import org.workfitai.jobservice.service.iJobService;
import org.workfitai.jobservice.util.ApiMessage;

import java.io.IOException;
import java.util.UUID;

import static org.workfitai.jobservice.util.MessageConstant.JOB_DELETED_SUCCESSFULLY;
import static org.workfitai.jobservice.util.MessageConstant.JOB_NOT_FOUND;
import static org.workfitai.jobservice.util.MessageConstant.JOB_REBUILD_SUCCESSFULLY;

import org.springframework.web.bind.annotation.PostMapping;

@RestController("adminJobController")
@RequestMapping("/admin/jobs")
public class JobController {
    private final iJobService jobService;
    private final ElasticJobService elasticService;

    public JobController(iJobService jobService, ElasticJobService elasticService) {
        this.jobService = jobService;
        this.elasticService = elasticService;
    }

    @PreAuthorize("hasAuthority('job:delete')")
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

    @PostMapping("/rebuild")
    @PreAuthorize("hasAuthority('job:read')")
    @ApiMessage(JOB_REBUILD_SUCCESSFULLY)
    public ResponseEntity<String> rebuild() {
        this.elasticService.rebuildJobIndex();
        return ResponseEntity.ok("Rebuild completed");
    }

}