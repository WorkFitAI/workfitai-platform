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
import org.workfitai.jobservice.model.JobReportSnapshot;
import org.workfitai.jobservice.model.Report;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.JobInfoDTO;
import org.workfitai.jobservice.model.dto.request.Report.ReqCreateReport;
import org.workfitai.jobservice.model.dto.response.Report.ResJobReportSnapshot;
import org.workfitai.jobservice.model.dto.response.Report.ResReport;
import org.workfitai.jobservice.model.dto.response.Report.ResReportGroup;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.model.enums.EReportStatus;
import org.workfitai.jobservice.model.mapper.ReportMapper;
import org.workfitai.jobservice.repository.JobReportSnapshotRepository;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.repository.ReportRepository;
import org.workfitai.jobservice.service.CloudinaryService;
import org.workfitai.jobservice.service.iReportService;
import org.workfitai.jobservice.util.PaginationUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.workfitai.jobservice.util.MessageConstant.*;

@Service
@Slf4j
public class ReportService implements iReportService {
        private final ReportRepository reportRepository;

        private final JobRepository jobRepository;

        private final JobReportSnapshotRepository snapshotRepository;

        private final CloudinaryService cloudinaryService;

        private final ReportMapper reportMapper;

        ReportService(final ReportRepository reportRepository, final JobRepository jobRepository,
                        final JobReportSnapshotRepository snapshotRepository, final CloudinaryService cloudinaryService,
                        final ReportMapper reportMapper) {
                this.reportRepository = reportRepository;
                this.jobRepository = jobRepository;
                this.snapshotRepository = snapshotRepository;
                this.cloudinaryService = cloudinaryService;
                this.reportMapper = reportMapper;
        }

        @Override
        @Transactional
        public String createReport(
                        ReqCreateReport req,
                        MultipartFile[] files,
                        String currentUser) throws IOException {

                Job job = jobRepository.findById(req.getJobId())
                                .orElseThrow(() -> new InvalidDataException(JOB_NOT_FOUND));

                boolean alreadyReported = reportRepository.existsByJob_JobIdAndCreatedBy(
                                job.getJobId(),
                                currentUser);

                if (alreadyReported) {
                        throw new ResourceConflictException(
                                        REPORT_CREATE_CONFLICT);
                }

                List<String> imageUrls = new ArrayList<>();

                if (files != null) {
                        for (MultipartFile file : files) {
                                imageUrls.add(
                                                cloudinaryService.uploadFile(
                                                                file,
                                                                "reports"));
                        }
                }

                Report report = reportMapper.toEntity(req);
                report.setJob(job);
                report.setImages(imageUrls);
                report.setStatus(EReportStatus.PENDING);

                report = reportRepository.save(report);

                JobReportSnapshot snapshot = JobReportSnapshot.builder()
                                .report(report)
                                .jobId(job.getJobId())
                                .title(job.getTitle())
                                .description(job.getDescription())
                                .shortDescription(job.getShortDescription())
                                .location(job.getLocation())
                                .currency(job.getCurrency())
                                .salaryMin(job.getSalaryMin())
                                .salaryMax(job.getSalaryMax())
                                .requirements(job.getRequirements())
                                .benefits(job.getBenefits())
                                .responsibilities(job.getResponsibilities())
                                .companyName(
                                                job.getCompany() != null
                                                                ? job.getCompany().getName()
                                                                : null)
                                .skills(
                                                job.getSkills() == null
                                                                ? ""
                                                                : job.getSkills()
                                                                                .stream()
                                                                                .map(Skill::getName)
                                                                                .collect(Collectors.joining(", ")))
                                .reportedAt(Instant.now())
                                .educationLevel(job.getEducationLevel())
                                .employmentType(job.getEmploymentType().toString())
                                .requiredExperience(job.getRequiredExperience())
                                .experienceLevel(job.getExperienceLevel().toString())

                                .build();

                snapshotRepository.save(snapshot);

                return REPORT_CREATED_SUCCESSFULLY;
        }

        @Override
        public ResultPaginationDTO fetchAllReportsGrouped(
                        Specification<Report> spec,
                        Pageable pageable) {

                List<ResReport> allReports = getReportDTOList(spec);

                if (allReports.isEmpty()) {
                        return PaginationUtils.toResultPaginationDTO(
                                        new PageImpl<>(List.of(), pageable, 0),
                                        Function.identity());
                }

                Set<UUID> jobIds = allReports.stream()
                                .map(ResReport::getJobId)
                                .collect(Collectors.toSet());

                Set<UUID> reportIds = allReports.stream()
                                .map(ResReport::getReportId)
                                .collect(Collectors.toSet());

                Map<UUID, JobInfoDTO> jobInfoMap = jobRepository
                                .findAllById(jobIds)
                                .stream()
                                .collect(Collectors.toMap(
                                                Job::getJobId,
                                                job -> new JobInfoDTO(
                                                                job.getCompany() != null
                                                                                ? job.getCompany().getName()
                                                                                : "Unknown",
                                                                job.isDeleted())));

                Map<UUID, JobReportSnapshot> snapshotMap = snapshotRepository
                                .findAllByReport_ReportIdIn(reportIds)
                                .stream()
                                .collect(Collectors.toMap(
                                                snapshot -> snapshot.getReport().getReportId(),
                                                Function.identity()));

                List<ResReportGroup> grouped = allReports.stream()
                                .collect(Collectors.groupingBy(
                                                ResReport::getJobId))
                                .entrySet()
                                .stream()
                                .map(entry -> {

                                        UUID jobId = entry.getKey();

                                        List<ResReport> reports = entry.getValue();

                                        JobInfoDTO jobInfo = jobInfoMap.get(jobId);

                                        ResReport latestReport = reports.stream()
                                                        .max(Comparator.comparing(
                                                                        ResReport::getCreatedDate))
                                                        .orElse(null);

                                        JobReportSnapshot snapshot = latestReport == null
                                                        ? null
                                                        : snapshotMap.get(
                                                                        latestReport.getReportId());
                                        EReportStatus groupStatus;

                                        if (reports.stream().anyMatch(r -> r.getStatus() == EReportStatus.PENDING)) {
                                                groupStatus = EReportStatus.PENDING;
                                        } else if (reports.stream()
                                                        .anyMatch(r -> r.getStatus() == EReportStatus.IN_PROGRESS)) {
                                                groupStatus = EReportStatus.IN_PROGRESS;
                                        } else if (reports.stream()
                                                        .anyMatch(r -> r.getStatus() == EReportStatus.RESOLVED)) {
                                                groupStatus = EReportStatus.RESOLVED;
                                        } else {
                                                groupStatus = EReportStatus.DECLINE;
                                        }

                                        return ResReportGroup.builder()
                                                        .jobId(jobId)
                                                        .companyName(
                                                                        snapshot != null
                                                                                        ? snapshot.getCompanyName()
                                                                                        : (jobInfo != null
                                                                                                        ? jobInfo.getCompanyName()
                                                                                                        : "Unknown"))
                                                        .isDeleted(
                                                                        jobInfo != null
                                                                                        && jobInfo.isDeleted())
                                                        .reportCount(reports.size())
                                                        .reports(reports)
                                                        .status(groupStatus)
                                                        .snapshot(
                                                                        snapshot == null
                                                                                        ? null
                                                                                        : ResJobReportSnapshot.builder()
                                                                                                        .snapshotId(
                                                                                                                        snapshot.getSnapshotId())
                                                                                                        .title(
                                                                                                                        snapshot.getTitle())
                                                                                                        .description(
                                                                                                                        snapshot.getDescription())
                                                                                                        .shortDescription(
                                                                                                                        snapshot.getShortDescription())
                                                                                                        .location(
                                                                                                                        snapshot.getLocation())
                                                                                                        .currency(
                                                                                                                        snapshot.getCurrency())
                                                                                                        .salaryMin(
                                                                                                                        snapshot.getSalaryMin())
                                                                                                        .salaryMax(
                                                                                                                        snapshot.getSalaryMax())
                                                                                                        .requirements(
                                                                                                                        snapshot.getRequirements())
                                                                                                        .benefits(
                                                                                                                        snapshot.getBenefits())
                                                                                                        .responsibilities(
                                                                                                                        snapshot.getResponsibilities())
                                                                                                        .skills(
                                                                                                                        snapshot.getSkills())
                                                                                                        .companyName(
                                                                                                                        snapshot.getCompanyName())
                                                                                                        .employmentType(
                                                                                                                        snapshot.getEmploymentType())
                                                                                                        .requiredExperience(
                                                                                                                        snapshot.getRequiredExperience())
                                                                                                        .experienceLevel(
                                                                                                                        snapshot.getExperienceLevel())
                                                                                                        .educationLevel(snapshot
                                                                                                                        .getEducationLevel())
                                                                                                        .reportedAt(
                                                                                                                        snapshot.getReportedAt())
                                                                                                        .build())
                                                        .build();
                                })
                                .sorted(
                                                Comparator.comparingInt(
                                                                ResReportGroup::getReportCount).reversed())
                                .toList();

                int total = grouped.size();

                int start = (int) pageable.getOffset();
                int end = Math.min(
                                start + pageable.getPageSize(),
                                total);

                List<ResReportGroup> pageContent = start < total
                                ? grouped.subList(start, end)
                                : List.of();

                Page<ResReportGroup> page = new PageImpl<>(
                                pageContent,
                                pageable,
                                total);

                return PaginationUtils.toResultPaginationDTO(
                                page,
                                Function.identity());
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
        public void changeStatus(
                        UUID jobId,
                        EReportStatus newStatus) {

                List<Report> reports = reportRepository.findAllByJobId(jobId);

                if (reports.isEmpty()) {
                        throw new ResourceNotFoundException(
                                        "No reports found for job: " + jobId);
                }

                for (Report report : reports) {

                        EReportStatus currentStatus = report.getStatus();

                        boolean validTransition = switch (newStatus) {
                                case IN_PROGRESS ->
                                        currentStatus == EReportStatus.PENDING;

                                case RESOLVED, DECLINE ->
                                        currentStatus == EReportStatus.PENDING
                                                        || currentStatus == EReportStatus.IN_PROGRESS;

                                default -> false;
                        };

                        if (!validTransition) {
                                throw new ResourceConflictException(
                                                "Cannot move report "
                                                                + report.getReportId()
                                                                + " from "
                                                                + currentStatus
                                                                + " to "
                                                                + newStatus);
                        }

                        report.setStatus(newStatus);
                }

                reportRepository.saveAll(reports);
        }
}
