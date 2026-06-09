package org.workfitai.jobservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.workfitai.jobservice.model.dto.response.Job.HrmJobStatsResponse;
import org.workfitai.jobservice.model.dto.response.Job.JobPlatformStatsResponse;
import org.workfitai.jobservice.model.enums.EReportStatus;
import org.workfitai.jobservice.model.enums.EmploymentType;
import org.workfitai.jobservice.model.enums.ExperienceLevel;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.repository.CompanyRepository;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.repository.ReportRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobPlatformStatsService {

    private static final int EXPIRY_WARNING_DAYS = 7;

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final ReportRepository reportRepository;

    public JobPlatformStatsResponse getAdminStats() {
        Instant now = Instant.now();
        Instant deadline = now.plus(EXPIRY_WARNING_DAYS, ChronoUnit.DAYS);

        Map<String, Long> byStatus = jobRepository.countByStatusRaw().stream()
                .collect(Collectors.toMap(
                        row -> ((JobStatus) row[0]).name(),
                        row -> (Long) row[1]
                ));

        long totalCompanies = companyRepository.count();
        long expiringSoon   = jobRepository.countExpiringSoon(now, deadline);
        long totalViews     = jobRepository.sumAllViews();
        long pendingReports = reportRepository.countByStatus(EReportStatus.PENDING);

        Map<String, Long> byEmploymentType = jobRepository.countPublishedByEmploymentType().stream()
                .collect(Collectors.toMap(
                        row -> ((EmploymentType) row[0]).name(),
                        row -> (Long) row[1]
                ));

        Map<String, Long> byExperienceLevel = jobRepository.countPublishedByExperienceLevel().stream()
                .collect(Collectors.toMap(
                        row -> ((ExperienceLevel) row[0]).name(),
                        row -> (Long) row[1]
                ));

        List<JobPlatformStatsResponse.TopJobByViews> topJobsByViews = jobRepository
                .findTop10PublishedByViewsDesc().stream()
                .map(j -> new JobPlatformStatsResponse.TopJobByViews(
                        j.getJobId().toString(),
                        j.getTitle(),
                        j.getCompany() != null ? j.getCompany().getName() : "Unknown",
                        j.getViews()))
                .toList();

        return new JobPlatformStatsResponse(byStatus, totalCompanies, expiringSoon, totalViews,
                pendingReports, byEmploymentType, byExperienceLevel, topJobsByViews);
    }

    public HrmJobStatsResponse getCompanyStats(String companyId) {
        Instant now = Instant.now();
        Instant deadline = now.plus(EXPIRY_WARNING_DAYS, ChronoUnit.DAYS);

        long published = jobRepository.countByCompanyCompanyNoAndStatusAndIsDeletedFalse(companyId, JobStatus.PUBLISHED);
        long draft     = jobRepository.countByCompanyCompanyNoAndStatusAndIsDeletedFalse(companyId, JobStatus.DRAFT);
        long closed    = jobRepository.countByCompanyCompanyNoAndStatusAndIsDeletedFalse(companyId, JobStatus.CLOSED);
        long expiring  = jobRepository.countExpiringByCompany(companyId, now, deadline);
        long reports   = reportRepository.countByJobCompanyAndStatus(companyId, EReportStatus.PENDING);

        Map<String, Long> byEmploymentType = jobRepository.countPublishedByEmploymentTypeForCompany(companyId).stream()
                .collect(Collectors.toMap(
                        row -> ((EmploymentType) row[0]).name(),
                        row -> (Long) row[1]
                ));

        Map<String, Long> byExperienceLevel = jobRepository.countPublishedByExperienceLevelForCompany(companyId).stream()
                .collect(Collectors.toMap(
                        row -> ((ExperienceLevel) row[0]).name(),
                        row -> (Long) row[1]
                ));

        List<HrmJobStatsResponse.TopJobByViews> topJobsByViews = jobRepository
                .findTop10PublishedByCompanyAndViewsDesc(companyId).stream()
                .map(j -> new HrmJobStatsResponse.TopJobByViews(
                        j.getJobId().toString(),
                        j.getTitle(),
                        j.getCompany() != null ? j.getCompany().getName() : "Unknown",
                        j.getViews()))
                .toList();

        return new HrmJobStatsResponse(published, draft, closed, expiring, reports,
                byEmploymentType, byExperienceLevel, topJobsByViews);
    }
}
