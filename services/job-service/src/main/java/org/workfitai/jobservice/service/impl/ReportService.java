package org.workfitai.jobservice.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.jobservice.config.errors.InvalidDataException;
import org.workfitai.jobservice.config.errors.ResourceConflictException;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.Report;
import org.workfitai.jobservice.model.dto.JobInfoDTO;
import org.workfitai.jobservice.model.dto.request.Report.ReqCreateReport;
import org.workfitai.jobservice.model.dto.response.Report.ResReport;
import org.workfitai.jobservice.model.dto.response.Report.ResReportGroup;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.model.enums.EReportStatus;
import org.workfitai.jobservice.model.mapper.ReportMapper;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.repository.ReportRepository;
import org.workfitai.jobservice.service.CloudinaryService;
import org.workfitai.jobservice.service.iReportService;
import org.workfitai.jobservice.util.PaginationUtils;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.workfitai.jobservice.util.MessageConstant.*;

@Service
@Slf4j
public class ReportService implements iReportService {
    private final ReportRepository reportRepository;

    private final JobRepository jobRepository;

    private final CloudinaryService cloudinaryService;

    private final ReportMapper reportMapper;

    ReportService(final ReportRepository reportRepository, final JobRepository jobRepository, final CloudinaryService cloudinaryService, final ReportMapper reportMapper) {
        this.reportRepository = reportRepository;
        this.jobRepository = jobRepository;
        this.cloudinaryService = cloudinaryService;
        this.reportMapper = reportMapper;
    }

    @Override
    public String createReport(
            ReqCreateReport req,
            MultipartFile[] files,
            String currentUser
    ) throws IOException, InvalidDataException {

        Job job = jobRepository.findById(req.getJobId())
                .orElseThrow(() -> new InvalidDataException(JOB_NOT_FOUND));

        boolean alreadyReported = reportRepository
                .existsByJob_JobIdAndCreatedBy(job.getJobId(), currentUser);

        if (alreadyReported) {
            throw new ResourceNotFoundException(REPORT_CREATE_CONFLICT);
        }

        List<String> imageUrls = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                String url = cloudinaryService.uploadFile(file, "reports");
                imageUrls.add(url);
            }
        }

        Report report = reportMapper.toEntity(req);
        report.setJob(job);
        report.setImages(imageUrls);

        reportRepository.save(report);

        return REPORT_CREATED_SUCCESSFULLY;
    }

    @Override
    public ResultPaginationDTO fetchAllReportsGrouped(
            Specification<Report> spec,
            Pageable pageable
    ) {
        // 1. Lấy toàn bộ report + map sang DTO
        List<ResReport> allReports = getReportDTOList(spec);

        // 2. Lấy tất cả jobId
        Set<UUID> jobIds = allReports.stream()
                .map(ResReport::getJobId)
                .collect(Collectors.toSet());

        // 3. Query Job + Company theo jobId
        Map<UUID, JobInfoDTO> jobInfoMap = jobRepository
                .findAllById(jobIds)
                .stream()
                .collect(Collectors.toMap(
                        Job::getJobId,
                        j -> new JobInfoDTO(
                                j.getCompany() != null
                                        ? j.getCompany().getName()
                                        : "Unknown",
                                j.isDeleted()
                        )
                ));

        // 4. Gom nhóm theo jobId + status
        List<ResReportGroup> grouped = allReports.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getJobId() + "|" + r.getStatus()
                ))
                .entrySet()
                .stream()
                .map(e -> {
                    String[] keys = e.getKey().split("\\|");
                    UUID jobId = UUID.fromString(keys[0]);
                    EReportStatus status = EReportStatus.valueOf(keys[1]);

                    List<ResReport> reports = e.getValue();
                    JobInfoDTO jobInfo = jobInfoMap.get(jobId);

                    return ResReportGroup.builder()
                            .jobId(jobId)
                            .companyName(
                                    jobInfo != null
                                            ? jobInfo.getCompanyName()
                                            : "Unknown"
                            )
                            .isDeleted(
                                    jobInfo != null
                            )
                            .status(status)
                            .reportCount(reports.size())
                            .reports(reports)
                            .build();
                })
                // sort job bị report nhiều nhất lên đầu
                .sorted((a, b) ->
                        Integer.compare(b.getReportCount(), a.getReportCount())
                )
                .toList();

        // 5. Phân trang GROUP
        int total = grouped.size();
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), total);

        List<ResReportGroup> pageContent =
                (start <= end) ? grouped.subList(start, end) : List.of();

        Page<ResReportGroup> page = new PageImpl<>(
                pageContent,
                pageable,
                total
        );

        // 6. Build response pagination chuẩn
        return PaginationUtils.toResultPaginationDTO(
                page,
                Function.identity()
        );
    }

    // Phương thức lấy tất cả report và map sang DTO
    @EntityGraph(attributePaths = "images")
    private List<ResReport> getReportDTOList(Specification<Report> spec) {
        List<Report> reports = reportRepository.findAll(spec);
        return reports.stream()
                .map(reportMapper::toDto)
                .toList();
    }

    @Transactional
    public void changeStatusByJobId(UUID jobId, EReportStatus newStatus) {
        List<EReportStatus> allowedCurrentStatuses = switch (newStatus) {
            case IN_PROGRESS -> List.of(EReportStatus.PENDING);
            case RESOLVED, DECLINE -> List.of(EReportStatus.PENDING, EReportStatus.IN_PROGRESS);
            default -> List.of();
        };

        if (allowedCurrentStatuses.isEmpty()) {
            throw new ResourceConflictException("Cannot move to status " + newStatus);
        }

        int updated = reportRepository.updateStatusWithWorkflow(
                jobId,
                newStatus,
                allowedCurrentStatuses
        );

        if (updated == 0) {
            throw new ResourceConflictException("No reports updated. Workflow violation or all reports already at " + newStatus);
        }
    }
}
