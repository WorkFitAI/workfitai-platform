package org.workfitai.jobservice.controller.Admin;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.jobservice.model.dto.response.Job.JobPlatformStatsResponse;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.service.impl.JobPlatformStatsService;
import org.workfitai.jobservice.util.ApiMessage;

@RestController("adminJobStatsController")
@RequestMapping("/admin/job-stats")
@RequiredArgsConstructor
public class JobStatsController {

    private final JobPlatformStatsService statsService;

    @GetMapping
    @PreAuthorize("hasAuthority('job:list')")
    @ApiMessage("Job platform stats fetched successfully")
    public RestResponse<JobPlatformStatsResponse> getPlatformStats() {
        return RestResponse.success(statsService.getAdminStats());
    }
}
