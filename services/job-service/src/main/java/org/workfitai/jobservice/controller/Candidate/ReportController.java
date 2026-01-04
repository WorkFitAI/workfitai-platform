package org.workfitai.jobservice.controller.Candidate;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.jobservice.model.dto.request.Report.ReqCreateReport;
import org.workfitai.jobservice.model.dto.response.RestResponse;
import org.workfitai.jobservice.security.SecurityUtils;
import org.workfitai.jobservice.service.iReportService;
import org.workfitai.jobservice.util.ApiMessage;

import static org.workfitai.jobservice.util.MessageConstant.REPORT_CREATED_SUCCESSFULLY;

@RestController
@RequestMapping("/candidate/reports")
public class ReportController {
    private final iReportService reportService;

    public ReportController(iReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ApiMessage(REPORT_CREATED_SUCCESSFULLY)
    public RestResponse<String> createReport(
            @RequestPart("data") ReqCreateReport req,
            @RequestPart(value = "files", required = false) MultipartFile[] files
    ) throws Exception {
        return RestResponse.success(
                reportService.createReport(req, files, SecurityUtils.getCurrentUser())
        );
    }
}
