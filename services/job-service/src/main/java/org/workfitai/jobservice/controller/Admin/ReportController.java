package org.workfitai.jobservice.controller.Admin;

import com.turkraft.springfilter.boot.Filter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.workfitai.jobservice.model.Report;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.model.enums.EReportStatus;
import org.workfitai.jobservice.service.iReportService;
import org.workfitai.jobservice.util.ApiMessage;

import java.util.UUID;

import static org.workfitai.jobservice.util.MessageConstant.REPORT_ALL_FETCHED_SUCCESSFULLY;
import static org.workfitai.jobservice.util.MessageConstant.REPORT_UPDATE_STATS_SUCCESSFULLY;


@RestController("adminReportController")
@RequestMapping("/admin/reports")
public class ReportController {

    private final iReportService reportService;

    public ReportController(iReportService reportService) {
        this.reportService = reportService;
    }

    @PreAuthorize("hasAuthority('report:create')")
    @GetMapping("/grouped")
    @ApiMessage(REPORT_ALL_FETCHED_SUCCESSFULLY)
    public RestResponse<ResultPaginationDTO> getReportsGrouped(
            @Filter Specification<Report> spec,
            @PageableDefault(size = 10, sort = "createdDate", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return RestResponse.success(reportService.fetchAllReportsGrouped(spec, pageable));
    }

    @PreAuthorize("hasAuthority('report:stats')")
    @PutMapping("/{jobId}/status/{newStatus}")
    @ApiMessage(REPORT_UPDATE_STATS_SUCCESSFULLY)
    public RestResponse<String> changeStatusByJobId(
            @PathVariable UUID jobId,
            @PathVariable EReportStatus newStatus
    ) {
        reportService.changeStatusByJobId(jobId, newStatus);
        return RestResponse.success("Changed status of reports for job " + jobId + " to " + newStatus);
    }
}
