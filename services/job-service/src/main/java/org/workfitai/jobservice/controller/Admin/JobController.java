package org.workfitai.jobservice.controller.Admin;

import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.jobservice.config.errors.InvalidDataException;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.service.iJobService;
import org.workfitai.jobservice.util.ApiMessage;

import java.util.UUID;

import static org.workfitai.jobservice.util.MessageConstant.JOB_DELETED_SUCCESSFULLY;
import static org.workfitai.jobservice.util.MessageConstant.JOB_NOT_FOUND;

@RestController("adminJobController")
@RequestMapping("/admin/jobs")
public class JobController {
    private final iJobService jobService;

    public JobController(iJobService jobService) {
        this.jobService = jobService;
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
}
