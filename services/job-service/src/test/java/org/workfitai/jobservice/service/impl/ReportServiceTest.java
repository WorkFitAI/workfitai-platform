package org.workfitai.jobservice.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.workfitai.jobservice.config.errors.InvalidDataException;
import org.workfitai.jobservice.config.errors.ResourceConflictException;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.JobReportSnapshot;
import org.workfitai.jobservice.model.Report;
import org.workfitai.jobservice.model.Skill;
import org.workfitai.jobservice.model.dto.request.Report.ReqCreateReport;
import org.workfitai.jobservice.model.dto.response.Report.ResReport;
import org.workfitai.jobservice.model.dto.response.ResultPaginationDTO;
import org.workfitai.jobservice.model.enums.EReportStatus;
import org.workfitai.jobservice.model.enums.EmploymentType;
import org.workfitai.jobservice.model.enums.ExperienceLevel;
import org.workfitai.jobservice.model.mapper.ReportMapper;
import org.workfitai.jobservice.repository.JobReportSnapshotRepository;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.repository.ReportRepository;
import org.workfitai.jobservice.service.CloudinaryService;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobReportSnapshotRepository snapshotRepository;
    @Mock
    private CloudinaryService cloudinaryService;
    @Mock
    private ReportMapper reportMapper;
    @InjectMocks
    private ReportService reportService;

    private Job job(UUID jobId) {
        Job job = new Job();
        job.setJobId(jobId);
        job.setTitle("Java Developer");
        job.setExperienceLevel(ExperienceLevel.SENIOR);
        job.setEmploymentType(EmploymentType.FULL_TIME);
        job.setCompany(Company.builder().companyNo("C-A").name("FPT").build());
        return job;
    }

    @Test
    void createReport_savesReportAndSnapshot_whenNotAlreadyReported() throws IOException {
        UUID jobId = UUID.randomUUID();
        Job job = job(jobId);
        ReqCreateReport req = new ReqCreateReport();
        req.setJobId(jobId);
        req.setReportContent("Inappropriate content");
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(reportRepository.existsByJob_JobIdAndCreatedBy(jobId, "alice")).thenReturn(false);
        Report mappedReport = Report.builder().build();
        when(reportMapper.toEntity(req)).thenReturn(mappedReport);
        when(reportRepository.save(mappedReport)).thenReturn(mappedReport);

        String result = reportService.createReport(req, null, "alice");

        assertThat(result).isNotBlank();
        assertThat(mappedReport.getJob()).isSameAs(job);
        assertThat(mappedReport.getStatus()).isEqualTo(EReportStatus.PENDING);
        verify(snapshotRepository).save(any());
        verify(reportRepository).save(mappedReport);
    }

    @Test
    void createReport_uploadsAttachedImages() throws IOException {
        UUID jobId = UUID.randomUUID();
        Job job = job(jobId);
        ReqCreateReport req = new ReqCreateReport();
        req.setJobId(jobId);
        req.setReportContent("Spam job");
        MultipartFile[] files = new MultipartFile[] {
                new MockMultipartFile("file", "evidence.png", "image/png", new byte[] { 1 }) };
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(reportRepository.existsByJob_JobIdAndCreatedBy(jobId, "alice")).thenReturn(false);
        Report mappedReport = Report.builder().build();
        when(reportMapper.toEntity(req)).thenReturn(mappedReport);
        when(cloudinaryService.uploadFile(any(), eq("reports"))).thenReturn("https://cdn/evidence.png");
        when(reportRepository.save(mappedReport)).thenReturn(mappedReport);

        reportService.createReport(req, files, "alice");

        assertThat(mappedReport.getImages()).containsExactly("https://cdn/evidence.png");
    }

    @Test
    void createReport_snapshotsNullCompany_whenJobHasNoCompany() throws IOException {
        UUID jobId = UUID.randomUUID();
        Job job = job(jobId);
        job.setCompany(null);
        job.setSkills(List.of(new Skill("Java"), new Skill("Spring")));
        ReqCreateReport req = new ReqCreateReport();
        req.setJobId(jobId);
        req.setReportContent("Inappropriate content");
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(reportRepository.existsByJob_JobIdAndCreatedBy(jobId, "alice")).thenReturn(false);
        Report mappedReport = Report.builder().build();
        when(reportMapper.toEntity(req)).thenReturn(mappedReport);
        when(reportRepository.save(mappedReport)).thenReturn(mappedReport);
        org.mockito.ArgumentCaptor<JobReportSnapshot> captor = org.mockito.ArgumentCaptor
                .forClass(JobReportSnapshot.class);

        reportService.createReport(req, null, "alice");

        verify(snapshotRepository).save(captor.capture());
        assertThat(captor.getValue().getCompanyName()).isNull();
        assertThat(captor.getValue().getSkills()).isEqualTo("Java, Spring");
    }

    @Test
    void createReport_throwsInvalidData_whenJobNotFound() {
        UUID jobId = UUID.randomUUID();
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());
        ReqCreateReport req = new ReqCreateReport();
        req.setJobId(jobId);

        assertThatThrownBy(() -> reportService.createReport(req, null, "alice"))
                .isInstanceOf(InvalidDataException.class);
    }

    @Test
    void createReport_throwsConflict_whenAlreadyReportedByUser() {
        UUID jobId = UUID.randomUUID();
        Job job = job(jobId);
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(reportRepository.existsByJob_JobIdAndCreatedBy(jobId, "alice")).thenReturn(true);
        ReqCreateReport req = new ReqCreateReport();
        req.setJobId(jobId);

        assertThatThrownBy(() -> reportService.createReport(req, null, "alice"))
                .isInstanceOf(ResourceConflictException.class);
        verify(reportRepository, never()).save(any());
    }

    @Test
    void changeStatus_movesPendingReportsToInProgress() {
        UUID jobId = UUID.randomUUID();
        Report report = Report.builder().reportId(UUID.randomUUID()).status(EReportStatus.PENDING).build();
        when(reportRepository.findAllByJobId(jobId)).thenReturn(List.of(report));

        reportService.changeStatus(jobId, EReportStatus.IN_PROGRESS);

        assertThat(report.getStatus()).isEqualTo(EReportStatus.IN_PROGRESS);
        verify(reportRepository).saveAll(List.of(report));
    }

    @Test
    void changeStatus_allowsPendingOrInProgressToResolve() {
        UUID jobId = UUID.randomUUID();
        Report report = Report.builder().reportId(UUID.randomUUID()).status(EReportStatus.IN_PROGRESS).build();
        when(reportRepository.findAllByJobId(jobId)).thenReturn(List.of(report));

        reportService.changeStatus(jobId, EReportStatus.RESOLVED);

        assertThat(report.getStatus()).isEqualTo(EReportStatus.RESOLVED);
    }

    @Test
    void changeStatus_allowsPendingOrInProgressToDecline() {
        UUID jobId = UUID.randomUUID();
        Report report = Report.builder().reportId(UUID.randomUUID()).status(EReportStatus.PENDING).build();
        when(reportRepository.findAllByJobId(jobId)).thenReturn(List.of(report));

        reportService.changeStatus(jobId, EReportStatus.DECLINE);

        assertThat(report.getStatus()).isEqualTo(EReportStatus.DECLINE);
    }

    @Test
    void changeStatus_throwsConflict_whenNewStatusIsPending() {
        UUID jobId = UUID.randomUUID();
        Report report = Report.builder().reportId(UUID.randomUUID()).status(EReportStatus.IN_PROGRESS).build();
        when(reportRepository.findAllByJobId(jobId)).thenReturn(List.of(report));

        assertThatThrownBy(() -> reportService.changeStatus(jobId, EReportStatus.PENDING))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test
    void changeStatus_throwsConflict_whenTransitionInvalid() {
        UUID jobId = UUID.randomUUID();
        Report report = Report.builder().reportId(UUID.randomUUID()).status(EReportStatus.RESOLVED).build();
        when(reportRepository.findAllByJobId(jobId)).thenReturn(List.of(report));

        assertThatThrownBy(() -> reportService.changeStatus(jobId, EReportStatus.IN_PROGRESS))
                .isInstanceOf(ResourceConflictException.class);
    }

    @Test
    void changeStatus_throwsResourceNotFound_whenNoReportsForJob() {
        UUID jobId = UUID.randomUUID();
        when(reportRepository.findAllByJobId(jobId)).thenReturn(List.of());

        assertThatThrownBy(() -> reportService.changeStatus(jobId, EReportStatus.RESOLVED))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void fetchAllReportsGrouped_returnsEmptyPage_whenNoReportsMatchSpec() {
        when(reportRepository.findAll((org.springframework.data.jpa.domain.Specification<Report>) null))
                .thenReturn(List.of());

        ResultPaginationDTO result = reportService.fetchAllReportsGrouped(null, PageRequest.of(0, 10));

        assertThat(result.getMeta().getTotal()).isEqualTo(0);
        assertThat((List<?>) result.getResult()).isEmpty();
    }

    @Test
    void fetchAllReportsGrouped_groupsReportsByJobAndPicksHighestPriorityStatus() {
        UUID jobId = UUID.randomUUID();
        Job job = job(jobId);
        UUID pendingReportId = UUID.randomUUID();
        UUID resolvedReportId = UUID.randomUUID();

        Report pendingEntity = Report.builder().reportId(pendingReportId).status(EReportStatus.PENDING).build();
        Report resolvedEntity = Report.builder().reportId(resolvedReportId).status(EReportStatus.RESOLVED).build();
        when(reportRepository.findAll((org.springframework.data.jpa.domain.Specification<Report>) null))
                .thenReturn(List.of(pendingEntity, resolvedEntity));

        ResReport pendingDto = ResReport.builder()
                .reportId(pendingReportId).jobId(jobId).status(EReportStatus.PENDING)
                .createdDate(Instant.now()).build();
        ResReport resolvedDto = ResReport.builder()
                .reportId(resolvedReportId).jobId(jobId).status(EReportStatus.RESOLVED)
                .createdDate(Instant.now().plusSeconds(60)).build();
        when(reportMapper.toDto(pendingEntity)).thenReturn(pendingDto);
        when(reportMapper.toDto(resolvedEntity)).thenReturn(resolvedDto);

        when(jobRepository.findAllById(any())).thenReturn(List.of(job));
        JobReportSnapshot snapshot = JobReportSnapshot.builder()
                .report(resolvedEntity).jobId(jobId).title("Java Developer").companyName("FPT").build();
        when(snapshotRepository.findAllByReport_ReportIdIn(any())).thenReturn(List.of(snapshot));

        ResultPaginationDTO result = reportService.fetchAllReportsGrouped(null, PageRequest.of(0, 10));

        assertThat(result.getMeta().getTotal()).isEqualTo(1);
    }

    @Test
    void fetchAllReportsGrouped_usesJobInfoFallback_whenNoSnapshotMatches() {
        UUID jobId = UUID.randomUUID();
        Job job = job(jobId);
        job.setCompany(null);
        job.setDeleted(true);
        UUID reportId = UUID.randomUUID();

        Report entity = Report.builder().reportId(reportId).status(EReportStatus.PENDING).build();
        when(reportRepository.findAll((org.springframework.data.jpa.domain.Specification<Report>) null))
                .thenReturn(List.of(entity));

        ResReport dto = ResReport.builder()
                .reportId(reportId).jobId(jobId).status(EReportStatus.PENDING)
                .createdDate(Instant.now()).build();
        when(reportMapper.toDto(entity)).thenReturn(dto);

        when(jobRepository.findAllById(any())).thenReturn(List.of(job));
        when(snapshotRepository.findAllByReport_ReportIdIn(any())).thenReturn(List.of());

        ResultPaginationDTO result = reportService.fetchAllReportsGrouped(null, PageRequest.of(0, 10));

        assertThat(result.getMeta().getTotal()).isEqualTo(1);
        List<?> content = (List<?>) result.getResult();
        org.workfitai.jobservice.model.dto.response.Report.ResReportGroup group =
                (org.workfitai.jobservice.model.dto.response.Report.ResReportGroup) content.get(0);
        assertThat(group.getCompanyName()).isEqualTo("Unknown");
        assertThat(group.isDeleted()).isTrue();
        assertThat(group.getSnapshot()).isNull();
    }

    @Test
    void fetchAllReportsGrouped_returnsEmptyPage_whenOffsetBeyondTotalGroups() {
        UUID jobId = UUID.randomUUID();
        Job job = job(jobId);
        UUID reportId = UUID.randomUUID();

        Report entity = Report.builder().reportId(reportId).status(EReportStatus.PENDING).build();
        when(reportRepository.findAll((org.springframework.data.jpa.domain.Specification<Report>) null))
                .thenReturn(List.of(entity));

        ResReport dto = ResReport.builder()
                .reportId(reportId).jobId(jobId).status(EReportStatus.PENDING)
                .createdDate(Instant.now()).build();
        when(reportMapper.toDto(entity)).thenReturn(dto);

        when(jobRepository.findAllById(any())).thenReturn(List.of(job));
        when(snapshotRepository.findAllByReport_ReportIdIn(any())).thenReturn(List.of());

        ResultPaginationDTO result = reportService.fetchAllReportsGrouped(null, PageRequest.of(5, 10));

        assertThat(result.getMeta().getTotal()).isEqualTo(1);
        assertThat((List<?>) result.getResult()).isEmpty();
    }
}
