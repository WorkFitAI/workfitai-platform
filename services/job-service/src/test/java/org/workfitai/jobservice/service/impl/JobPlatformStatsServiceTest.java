package org.workfitai.jobservice.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.jobservice.model.Company;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.dto.response.Job.HrmJobStatsResponse;
import org.workfitai.jobservice.model.dto.response.Job.JobPlatformStatsResponse;
import org.workfitai.jobservice.model.enums.EReportStatus;
import org.workfitai.jobservice.model.enums.EmploymentType;
import org.workfitai.jobservice.model.enums.ExperienceLevel;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.repository.CompanyRepository;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.repository.ReportRepository;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPlatformStatsServiceTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private ReportRepository reportRepository;
    @InjectMocks
    private JobPlatformStatsService jobPlatformStatsService;

    private Job jobWithViews(String title, long views) {
        Job job = new Job();
        job.setJobId(UUID.randomUUID());
        job.setTitle(title);
        job.setViews(views);
        job.setCompany(Company.builder().companyNo("C-A").name("FPT").build());
        return job;
    }

    @Test
    void getAdminStats_aggregatesAcrossAllSources() {
        when(jobRepository.countByStatusRaw())
                .thenReturn(Collections.singletonList(new Object[] { JobStatus.PUBLISHED, 10L }));
        when(companyRepository.count()).thenReturn(3L);
        when(jobRepository.countExpiringSoon(any(), any())).thenReturn(2L);
        when(jobRepository.sumAllViews()).thenReturn(500L);
        when(reportRepository.countByStatus(EReportStatus.PENDING)).thenReturn(4L);
        when(jobRepository.countPublishedByEmploymentType())
                .thenReturn(Collections.singletonList(new Object[] { EmploymentType.FULL_TIME, 7L }));
        when(jobRepository.countPublishedByJobCategory())
                .thenReturn(Collections.singletonList(new Object[] { "Engineering", 5L }));
        when(jobRepository.countPublishedByExperienceLevel())
                .thenReturn(Collections.singletonList(new Object[] { ExperienceLevel.SENIOR, 3L }));
        when(jobRepository.findTop10PublishedByViewsDesc()).thenReturn(List.of(jobWithViews("Java Dev", 99)));

        JobPlatformStatsResponse stats = jobPlatformStatsService.getAdminStats();

        assertThat(stats.totalJobsByStatus()).containsEntry("PUBLISHED", 10L);
        assertThat(stats.totalCompanies()).isEqualTo(3L);
        assertThat(stats.jobsExpiringSoon()).isEqualTo(2L);
        assertThat(stats.totalJobViews()).isEqualTo(500L);
        assertThat(stats.pendingReports()).isEqualTo(4L);
        assertThat(stats.byEmploymentType()).containsEntry("FULL_TIME", 7L);
        assertThat(stats.byJobCategory()).containsEntry("Engineering", 5L);
        assertThat(stats.byExperienceLevel()).containsEntry("SENIOR", 3L);
        assertThat(stats.topJobsByViews()).hasSize(1);
        assertThat(stats.topJobsByViews().get(0).title()).isEqualTo("Java Dev");
        assertThat(stats.topJobsByViews().get(0).companyName()).isEqualTo("FPT");
    }

    @Test
    void getCompanyStats_aggregatesScopedToCompany() {
        String companyId = "C-A";
        when(jobRepository.countByCompanyCompanyNoAndStatusAndIsDeletedFalse(companyId, JobStatus.PUBLISHED))
                .thenReturn(8L);
        when(jobRepository.countByCompanyCompanyNoAndStatusAndIsDeletedFalse(companyId, JobStatus.DRAFT))
                .thenReturn(2L);
        when(jobRepository.countByCompanyCompanyNoAndStatusAndIsDeletedFalse(companyId, JobStatus.CLOSED))
                .thenReturn(1L);
        when(jobRepository.countExpiringByCompany(eq(companyId), any(), any())).thenReturn(3L);
        when(reportRepository.countByJobCompanyAndStatus(companyId, EReportStatus.PENDING)).thenReturn(2L);
        when(jobRepository.countPublishedByEmploymentTypeForCompany(companyId))
                .thenReturn(Collections.singletonList(new Object[] { EmploymentType.REMOTE, 4L }));
        when(jobRepository.countPublishedByJobCategoryForCompany(companyId))
                .thenReturn(Collections.singletonList(new Object[] { "Design", 1L }));
        when(jobRepository.countPublishedByExperienceLevelForCompany(companyId))
                .thenReturn(Collections.singletonList(new Object[] { ExperienceLevel.JUNIOR, 6L }));
        when(jobRepository.findTop10PublishedByCompanyAndViewsDesc(companyId))
                .thenReturn(List.of(jobWithViews("Python Dev", 42)));

        HrmJobStatsResponse stats = jobPlatformStatsService.getCompanyStats(companyId);

        assertThat(stats.totalPublished()).isEqualTo(8L);
        assertThat(stats.totalDraft()).isEqualTo(2L);
        assertThat(stats.totalClosed()).isEqualTo(1L);
        assertThat(stats.expiringInWeek()).isEqualTo(3L);
        assertThat(stats.pendingReports()).isEqualTo(2L);
        assertThat(stats.byEmploymentType()).containsEntry("REMOTE", 4L);
        assertThat(stats.byJobCategory()).containsEntry("Design", 1L);
        assertThat(stats.byExperienceLevel()).containsEntry("JUNIOR", 6L);
        assertThat(stats.topJobsByViews()).hasSize(1);
        assertThat(stats.topJobsByViews().get(0).title()).isEqualTo("Python Dev");
    }
}
