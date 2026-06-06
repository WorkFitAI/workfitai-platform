package org.workfitai.jobservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.workfitai.jobservice.model.dto.response.Job.HrmJobStatsResponse;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.service.impl.JobPlatformStatsService;
import org.workfitai.jobservice.util.ApiMessage;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalController {

    private final JobPlatformStatsService statsService;

    @GetMapping("/company/{companyId}/job-stats")
    @ApiMessage("Company job stats fetched successfully")
    public RestResponse<HrmJobStatsResponse> getCompanyStats(@PathVariable String companyId) {
        return RestResponse.success(statsService.getCompanyStats(companyId));
    }
}
