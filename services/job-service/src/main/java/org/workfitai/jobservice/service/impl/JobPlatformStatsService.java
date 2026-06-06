package org.workfitai.jobservice.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.workfitai.jobservice.model.dto.response.Job.HrmJobStatsResponse;
import org.workfitai.jobservice.model.dto.response.Job.JobPlatformStatsResponse;
import org.workfitai.jobservice.model.enums.EReportStatus;
import org.workfitai.jobservice.model.enums.JobStatus;
import org.workfitai.jobservice.repository.CompanyRepository;
import org.workfitai.jobservice.repository.JobRepository;
import org.workfitai.jobservice.repository.ReportRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

        return new JobPlatformStatsResponse(byStatus, totalCompanies, expiringSoon, totalViews, pendingReports);
    }

    public HrmJobStatsResponse getCompanyStats(String companyId) {
        Instant now = Instant.now();
        Instant deadline = now.plus(EXPIRY_WARNING_DAYS, ChronoUnit.DAYS);

        long published = jobRepository.countByCompanyCompanyNoAndStatusAndIsDeletedFalse(companyId, JobStatus.PUBLISHED);
        long draft     = jobRepository.countByCompanyCompanyNoAndStatusAndIsDeletedFalse(companyId, JobStatus.DRAFT);
        long closed    = jobRepository.countByCompanyCompanyNoAndStatusAndIsDeletedFalse(companyId, JobStatus.CLOSED);
        long expiring  = jobRepository.countExpiringByCompany(companyId, now, deadline);
        long reports   = reportRepository.countByJobCompanyAndStatus(companyId, EReportStatus.PENDING);

        return new HrmJobStatsResponse(published, draft, closed, expiring, reports);
    }
}
