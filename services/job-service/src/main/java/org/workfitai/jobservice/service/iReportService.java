package org.workfitai.jobservice.service;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.jobservice.config.errors.InvalidDataException;
import org.workfitai.jobservice.model.Report;
import org.workfitai.jobservice.model.dto.request.Report.ReqCreateReport;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.model.enums.EReportStatus;

import java.io.IOException;
import java.util.UUID;

public interface iReportService {
    String createReport(
            ReqCreateReport req,
            MultipartFile[] files,
            String currentUser
    ) throws IOException, InvalidDataException;

    ResultPaginationDTO fetchAllReportsGrouped(Specification<Report> spec, Pageable pageable);

    @Transactional
    void changeStatusByJobId(UUID jobId, EReportStatus newStatus);
}
