package org.workfitai.jobservice.aspect;

import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.workfitai.jobservice.dto.kafka.NotificationEvent;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.dto.request.JobCategory.ReqUpdateJobCategoryDTO;
import org.workfitai.jobservice.model.dto.request.Recommendation.ReqJobRecommendationDTO;
import org.workfitai.jobservice.model.dto.request.Report.ReqCreateReport;
import org.workfitai.jobservice.model.dto.request.Skill.ReqUpdateSkillDTO;
import org.workfitai.jobservice.model.dto.response.Company.ResCompanyDTO;
import org.workfitai.jobservice.model.dto.response.Recommendation.ResJobRecommendationDTO;
import org.workfitai.jobservice.model.enums.EReportStatus;
import org.workfitai.jobservice.service.AuditLogService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobAuditAspectTest {

    @Mock
    private AuditLogService auditLogService;
    @InjectMocks
    private JobAuditAspect aspect;
    @Mock
    private JoinPoint joinPoint;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void authenticateAsHr() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("hr1").build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_HR"))));
    }

    private void triggerAfterCommit() {
        org.springframework.transaction.support.TransactionSynchronizationUtils.triggerAfterCommit();
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ───────────────────────── JOB ─────────────────────────

    @Test
    void afterCreateJob_logsActionWithoutTransaction_whenNoTransactionActive() {
        ResCompanyDTO result = ResCompanyDTO.builder().companyNo("job-1").build();

        aspect.afterCreateJob(joinPoint, result);

        verify(auditLogService).logAction(eq("JOB"), eq("job-1"), eq("JOB_CREATED"), eq("SYSTEM"),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void afterCreateJob_logsActionAfterCommit_whenTransactionActive() {
        authenticateAsHr();
        ResCompanyDTO result = ResCompanyDTO.builder().companyNo("job-1").build();
        TransactionSynchronizationManager.initSynchronization();

        aspect.afterCreateJob(joinPoint, result);
        org.springframework.transaction.support.TransactionSynchronizationUtils.triggerAfterCommit();
        TransactionSynchronizationManager.clearSynchronization();

        verify(auditLogService).logAction(eq("JOB"), eq("job-1"), eq("JOB_CREATED"), eq("hr1"),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void afterCreateJob_usesUnknownId_whenResultNotAuditableResponse() {
        aspect.afterCreateJob(joinPoint, "plain-string-result");

        verify(auditLogService).logAction(eq("JOB"), eq("unknown"), eq("JOB_CREATED"), eq("SYSTEM"),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void afterCreateJob_logsRollbackWarning_whenTransactionRolledBack() {
        ResCompanyDTO result = ResCompanyDTO.builder().companyNo("job-1").build();
        TransactionSynchronizationManager.initSynchronization();

        aspect.afterCreateJob(joinPoint, result);
        org.springframework.transaction.support.TransactionSynchronizationUtils.triggerAfterCompletion(
                org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
        TransactionSynchronizationManager.clearSynchronization();

        verify(auditLogService, never()).logAction(eq("JOB"), eq("job-1"), eq("JOB_CREATED"), anyString(),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void currentUsername_returnsSystem_whenAuthenticationNotAuthenticated() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("hr1").build();
        JwtAuthenticationToken unauthenticated = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_HR")));
        unauthenticated.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);
        ResCompanyDTO result = ResCompanyDTO.builder().companyNo("job-1").build();

        aspect.afterCreateJob(joinPoint, result);

        verify(auditLogService).logAction(eq("JOB"), eq("job-1"), eq("JOB_CREATED"), eq("SYSTEM"),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void logJobCreateFailed_logsFailure() {
        aspect.logJobCreateFailed(joinPoint, new RuntimeException("boom"));

        verify(auditLogService).logFailure(eq("JOB"), eq("unknown"), eq("JOB_CREATED"), eq("SYSTEM"),
                eq("boom"), anyMap());
    }

    @Test
    void afterUpdateJob_logsActionWithoutTransaction() {
        ResCompanyDTO result = ResCompanyDTO.builder().companyNo("job-1").build();

        aspect.afterUpdateJob(joinPoint, result);

        verify(auditLogService).logAction(eq("JOB"), eq("job-1"), eq("JOB_UPDATED"), anyString(),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void afterUpdateJob_logsActionAfterCommit_whenTransactionActive() {
        ResCompanyDTO result = ResCompanyDTO.builder().companyNo("job-1").build();
        TransactionSynchronizationManager.initSynchronization();

        aspect.afterUpdateJob(joinPoint, result);
        triggerAfterCommit();

        verify(auditLogService).logAction(eq("JOB"), eq("job-1"), eq("JOB_UPDATED"), anyString(),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void logJobUpdateFailed_logsFailure() {
        aspect.logJobUpdateFailed(joinPoint, new RuntimeException("update failed"));

        verify(auditLogService).logFailure(eq("JOB"), eq("unknown"), eq("JOB_UPDATED"), anyString(),
                eq("update failed"), anyMap());
    }

    @Test
    void afterDeleteJob_logsAction() {
        UUID jobId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[] { jobId });

        aspect.afterDeleteJob(joinPoint);

        verify(auditLogService).logAction(eq("JOB"), eq(jobId.toString()), eq("JOB_SOFT_DELETED"), anyString(),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void logJobDeleteFailed_logsFailure() {
        UUID jobId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[] { jobId });

        aspect.logJobDeleteFailed(joinPoint, new RuntimeException("delete failed"));

        verify(auditLogService).logFailure(eq("JOB"), eq(jobId.toString()), eq("JOB_SOFT_DELETED"), anyString(),
                eq("delete failed"), anyMap());
    }

    @Test
    void afterUpdateStatus_logsActionWithoutTransaction() {
        Job job = new Job();
        job.setJobId(UUID.randomUUID());
        when(joinPoint.getArgs()).thenReturn(new Object[] { job });

        aspect.afterUpdateStatus(joinPoint, null);

        verify(auditLogService).logAction(eq("JOB"), eq(job.getJobId().toString()), eq("JOB_STATUS_UPDATED"),
                anyString(), isNull(), anyMap(), anyMap());
    }

    @Test
    void afterUpdateStatus_logsActionAfterCommit_whenTransactionActive() {
        Job job = new Job();
        job.setJobId(UUID.randomUUID());
        when(joinPoint.getArgs()).thenReturn(new Object[] { job });
        TransactionSynchronizationManager.initSynchronization();

        aspect.afterUpdateStatus(joinPoint, null);
        triggerAfterCommit();

        verify(auditLogService).logAction(eq("JOB"), eq(job.getJobId().toString()), eq("JOB_STATUS_UPDATED"),
                anyString(), isNull(), anyMap(), anyMap());
    }

    @Test
    void logStatusUpdateFailed_logsFailure() {
        Job job = new Job();
        job.setJobId(UUID.randomUUID());
        when(joinPoint.getArgs()).thenReturn(new Object[] { job });

        aspect.logStatusUpdateFailed(joinPoint, new RuntimeException("status failed"));

        verify(auditLogService).logFailure(eq("JOB"), eq(job.getJobId().toString()), eq("JOB_STATUS_UPDATED"),
                anyString(), eq("status failed"), anyMap());
    }

    // ───────────────────────── COMPANY ─────────────────────────

    @Test
    void afterCreateCompany_logsActionWithoutTransaction() {
        ResCompanyDTO result = ResCompanyDTO.builder().companyNo("C-A").build();

        aspect.afterCreateCompany(joinPoint, result);

        verify(auditLogService).logAction(eq("COMPANY"), eq("C-A"), eq("COMPANY_CREATED"), anyString(),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void afterCreateCompany_logsActionAfterCommit_whenTransactionActive() {
        ResCompanyDTO result = ResCompanyDTO.builder().companyNo("C-A").build();
        TransactionSynchronizationManager.initSynchronization();

        aspect.afterCreateCompany(joinPoint, result);
        triggerAfterCommit();

        verify(auditLogService).logAction(eq("COMPANY"), eq("C-A"), eq("COMPANY_CREATED"), anyString(),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void afterUpdateCompany_logsActionWithoutTransaction() {
        ResCompanyDTO result = ResCompanyDTO.builder().companyNo("C-A").build();

        aspect.afterUpdateCompany(joinPoint, result);

        verify(auditLogService).logAction(eq("COMPANY"), eq("C-A"), eq("COMPANY_UPDATED"), anyString(),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void afterUpdateCompany_logsActionAfterCommit_whenTransactionActive() {
        ResCompanyDTO result = ResCompanyDTO.builder().companyNo("C-A").build();
        TransactionSynchronizationManager.initSynchronization();

        aspect.afterUpdateCompany(joinPoint, result);
        triggerAfterCommit();

        verify(auditLogService).logAction(eq("COMPANY"), eq("C-A"), eq("COMPANY_UPDATED"), anyString(),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void updateCompanyFailed_logsFailure() {
        aspect.updateCompanyFailed(joinPoint, new RuntimeException("company update failed"));

        verify(auditLogService).logFailure(eq("COMPANY"), eq("unknown"), eq("COMPANY_UPDATED"), anyString(),
                eq("company update failed"), anyMap());
    }

    @Test
    void afterDeleteCompany_logsAction() {
        when(joinPoint.getArgs()).thenReturn(new Object[] { "C-A" });

        aspect.afterDeleteCompany(joinPoint);

        verify(auditLogService).logAction(eq("COMPANY"), eq("C-A"), eq("COMPANY_DELETED"), anyString(),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void deleteCompanyFailed_logsFailure() {
        when(joinPoint.getArgs()).thenReturn(new Object[] { "C-A" });

        aspect.deleteCompanyFailed(joinPoint, new RuntimeException("company delete failed"));

        verify(auditLogService).logFailure(eq("COMPANY"), eq("C-A"), eq("COMPANY_DELETED"), anyString(),
                eq("company delete failed"), anyMap());
    }

    // ───────────────────────── SKILL ─────────────────────────

    @Test
    void afterCreateSkill_logsActionWithoutTransaction() {
        UUID skillId = UUID.randomUUID();
        org.workfitai.jobservice.model.dto.response.Skill.ResSkillDTO result =
                org.workfitai.jobservice.model.dto.response.Skill.ResSkillDTO.builder().skillId(skillId).build();

        aspect.afterCreateSkill(joinPoint, result);

        verify(auditLogService).logAction(eq("SKILL"), eq(skillId.toString()), eq("SKILL_CREATED"), anyString(),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void afterCreateSkill_logsActionAfterCommit_whenTransactionActive() {
        UUID skillId = UUID.randomUUID();
        org.workfitai.jobservice.model.dto.response.Skill.ResSkillDTO result =
                org.workfitai.jobservice.model.dto.response.Skill.ResSkillDTO.builder().skillId(skillId).build();
        TransactionSynchronizationManager.initSynchronization();

        aspect.afterCreateSkill(joinPoint, result);
        triggerAfterCommit();

        verify(auditLogService).logAction(eq("SKILL"), eq(skillId.toString()), eq("SKILL_CREATED"), anyString(),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void logSkillCreateFailed_logsFailure() {
        aspect.logSkillCreateFailed(joinPoint, new RuntimeException("skill create failed"));

        verify(auditLogService).logFailure(eq("SKILL"), eq("unknown"), eq("SKILL_CREATED"), anyString(),
                eq("skill create failed"), anyMap());
    }

    @Test
    void afterUpdateSkill_logsActionWithoutTransaction() {
        UUID skillId = UUID.randomUUID();
        ReqUpdateSkillDTO dto = ReqUpdateSkillDTO.builder().skillId(skillId).name("Java 21").build();
        when(joinPoint.getArgs()).thenReturn(new Object[] { dto });

        aspect.afterUpdateSkill(joinPoint);

        verify(auditLogService).logAction(eq("SKILL"), eq(skillId.toString()), eq("SKILL_UPDATED"), anyString(),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void afterUpdateSkill_logsActionAfterCommit_whenTransactionActive() {
        UUID skillId = UUID.randomUUID();
        ReqUpdateSkillDTO dto = ReqUpdateSkillDTO.builder().skillId(skillId).name("Java 21").build();
        when(joinPoint.getArgs()).thenReturn(new Object[] { dto });
        TransactionSynchronizationManager.initSynchronization();

        aspect.afterUpdateSkill(joinPoint);
        triggerAfterCommit();

        verify(auditLogService).logAction(eq("SKILL"), eq(skillId.toString()), eq("SKILL_UPDATED"), anyString(),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void afterUpdateSkill_logsRollbackWarning_whenTransactionRolledBack() {
        UUID skillId = UUID.randomUUID();
        ReqUpdateSkillDTO dto = ReqUpdateSkillDTO.builder().skillId(skillId).name("Java 21").build();
        when(joinPoint.getArgs()).thenReturn(new Object[] { dto });
        TransactionSynchronizationManager.initSynchronization();

        aspect.afterUpdateSkill(joinPoint);
        org.springframework.transaction.support.TransactionSynchronizationUtils.triggerAfterCompletion(
                org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
        TransactionSynchronizationManager.clearSynchronization();

        verify(auditLogService, never()).logAction(eq("SKILL"), eq(skillId.toString()), eq("SKILL_UPDATED"),
                anyString(), isNull(), anyMap(), anyMap());
    }

    @Test
    void updateSkillFailed_logsFailure() {
        UUID skillId = UUID.randomUUID();
        ReqUpdateSkillDTO dto = ReqUpdateSkillDTO.builder().skillId(skillId).name("Java 21").build();
        when(joinPoint.getArgs()).thenReturn(new Object[] { dto });

        aspect.updateSkillFailed(joinPoint, new RuntimeException("skill update failed"));

        verify(auditLogService).logFailure(eq("SKILL"), eq(skillId.toString()), eq("SKILL_UPDATED"), anyString(),
                eq("skill update failed"), anyMap());
    }

    @Test
    void afterDeleteSkill_logsAction() {
        UUID skillId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[] { skillId });

        aspect.afterDeleteSkill(joinPoint);

        verify(auditLogService).logAction(eq("SKILL"), eq(skillId.toString()), eq("SKILL_DELETED"), anyString(),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void deleteSkillFailed_logsFailure() {
        UUID skillId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[] { skillId });

        aspect.deleteSkillFailed(joinPoint, new RuntimeException("skill delete failed"));

        verify(auditLogService).logFailure(eq("SKILL"), eq(skillId.toString()), eq("SKILL_DELETED"), anyString(),
                eq("skill delete failed"), anyMap());
    }

    // ───────────────────────── JOB CATEGORY ─────────────────────────

    @Test
    void afterCreateJobCategory_logsActionWithoutTransaction() {
        UUID categoryId = UUID.randomUUID();
        org.workfitai.jobservice.model.dto.response.JobCategory.ResJobCategoryDTO result =
                new org.workfitai.jobservice.model.dto.response.JobCategory.ResJobCategoryDTO();
        result.setId(categoryId);

        aspect.afterCreateJobCategory(joinPoint, result);

        verify(auditLogService).logAction(eq("JOB_CATEGORY"), eq(categoryId.toString()),
                eq("JOB_CATEGORY_CREATED"), anyString(), isNull(), anyMap(), anyMap());
    }

    @Test
    void afterCreateJobCategory_logsActionAfterCommit_whenTransactionActive() {
        UUID categoryId = UUID.randomUUID();
        org.workfitai.jobservice.model.dto.response.JobCategory.ResJobCategoryDTO result =
                new org.workfitai.jobservice.model.dto.response.JobCategory.ResJobCategoryDTO();
        result.setId(categoryId);
        TransactionSynchronizationManager.initSynchronization();

        aspect.afterCreateJobCategory(joinPoint, result);
        triggerAfterCommit();

        verify(auditLogService).logAction(eq("JOB_CATEGORY"), eq(categoryId.toString()),
                eq("JOB_CATEGORY_CREATED"), anyString(), isNull(), anyMap(), anyMap());
    }

    @Test
    void logJobCategoryCreateFailed_logsFailure() {
        aspect.logJobCategoryCreateFailed(joinPoint, new RuntimeException("category create failed"));

        verify(auditLogService).logFailure(eq("JOB_CATEGORY"), eq("unknown"), eq("JOB_CATEGORY_CREATED"),
                anyString(), eq("category create failed"), anyMap());
    }

    @Test
    void afterUpdateCategory_logsActionWithoutTransaction() {
        UUID categoryId = UUID.randomUUID();
        org.workfitai.jobservice.model.dto.response.JobCategory.ResJobCategoryDTO result =
                new org.workfitai.jobservice.model.dto.response.JobCategory.ResJobCategoryDTO();
        result.setId(categoryId);

        aspect.afterUpdateCategory(joinPoint, result);

        verify(auditLogService).logAction(eq("JOB_CATEGORY"), eq(categoryId.toString()), eq("CATEGORY_UPDATED"),
                anyString(), isNull(), anyMap(), anyMap());
    }

    @Test
    void afterUpdateCategory_logsActionAfterCommit_whenTransactionActive() {
        UUID categoryId = UUID.randomUUID();
        org.workfitai.jobservice.model.dto.response.JobCategory.ResJobCategoryDTO result =
                new org.workfitai.jobservice.model.dto.response.JobCategory.ResJobCategoryDTO();
        result.setId(categoryId);
        TransactionSynchronizationManager.initSynchronization();

        aspect.afterUpdateCategory(joinPoint, result);
        triggerAfterCommit();

        verify(auditLogService).logAction(eq("JOB_CATEGORY"), eq(categoryId.toString()), eq("CATEGORY_UPDATED"),
                anyString(), isNull(), anyMap(), anyMap());
    }

    @Test
    void updateCategoryFailed_logsFailure_usingCategoryIdFromRequest() {
        UUID categoryId = UUID.randomUUID();
        ReqUpdateJobCategoryDTO dto = new ReqUpdateJobCategoryDTO();
        dto.setId(categoryId);
        when(joinPoint.getArgs()).thenReturn(new Object[] { dto });

        aspect.updateCategoryFailed(joinPoint, new RuntimeException("category update failed"));

        verify(auditLogService).logFailure(eq("JOB_CATEGORY"), eq(categoryId.toString()), eq("CATEGORY_UPDATED"),
                anyString(), eq("category update failed"), anyMap());
    }

    @Test
    void updateCategoryFailed_logsUnknown_whenIdMissing() {
        ReqUpdateJobCategoryDTO dto = new ReqUpdateJobCategoryDTO();
        when(joinPoint.getArgs()).thenReturn(new Object[] { dto });

        aspect.updateCategoryFailed(joinPoint, new RuntimeException("category update failed"));

        verify(auditLogService).logFailure(eq("JOB_CATEGORY"), eq("unknown"), eq("CATEGORY_UPDATED"),
                anyString(), eq("category update failed"), anyMap());
    }

    @Test
    void afterDeleteCategory_logsAction() {
        UUID categoryId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[] { categoryId });

        aspect.afterDeleteCategory(joinPoint);

        verify(auditLogService).logAction(eq("JOB_CATEGORY"), eq(categoryId.toString()), eq("CATEGORY_DELETED"),
                anyString(), isNull(), anyMap(), anyMap());
    }

    @Test
    void deleteCategoryFailed_logsFailure() {
        UUID categoryId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[] { categoryId });

        aspect.deleteCategoryFailed(joinPoint, new RuntimeException("category delete failed"));

        verify(auditLogService).logFailure(eq("JOB_CATEGORY"), eq(categoryId.toString()), eq("CATEGORY_DELETED"),
                anyString(), eq("category delete failed"), anyMap());
    }

    // ───────────────────────── REPORT ─────────────────────────

    @Test
    void afterCreateReport_logsAction() {
        UUID jobId = UUID.randomUUID();
        ReqCreateReport req = new ReqCreateReport();
        req.setJobId(jobId);
        when(joinPoint.getArgs()).thenReturn(new Object[] { req });

        aspect.afterCreateReport(joinPoint, "Report created successfully");

        verify(auditLogService).logAction(eq("REPORT"), eq(jobId.toString()), eq("REPORT_CREATED"), anyString(),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void createReportFailed_logsFailure_whenJobIdAvailable() {
        UUID jobId = UUID.randomUUID();
        ReqCreateReport req = new ReqCreateReport();
        req.setJobId(jobId);
        when(joinPoint.getArgs()).thenReturn(new Object[] { req });

        aspect.createReportFailed(joinPoint, new RuntimeException("report create failed"));

        verify(auditLogService).logFailure(eq("REPORT"), eq(jobId.toString()), eq("REPORT_CREATED"), anyString(),
                eq("report create failed"), anyMap());
    }

    @Test
    void createReportFailed_logsFailureAsUnknown_whenArgsEmpty() {
        when(joinPoint.getArgs()).thenReturn(new Object[0]);

        aspect.createReportFailed(joinPoint, new RuntimeException("report create failed"));

        verify(auditLogService).logFailure(eq("REPORT"), eq("unknown"), eq("REPORT_CREATED"), anyString(),
                eq("report create failed"), anyMap());
    }

    @Test
    void afterUpdateReportStatus_logsActionWithoutTransaction() {
        UUID reportId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[] { reportId, EReportStatus.RESOLVED });

        aspect.afterUpdateReportStatus(joinPoint);

        verify(auditLogService).logAction(eq("REPORT"), eq(reportId.toString()), eq("REPORT_STATUS_UPDATED"),
                anyString(), isNull(), anyMap(), anyMap());
    }

    @Test
    void afterUpdateReportStatus_logsActionAfterCommit_whenTransactionActive() {
        UUID reportId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[] { reportId, EReportStatus.RESOLVED });
        TransactionSynchronizationManager.initSynchronization();

        aspect.afterUpdateReportStatus(joinPoint);
        triggerAfterCommit();

        verify(auditLogService).logAction(eq("REPORT"), eq(reportId.toString()), eq("REPORT_STATUS_UPDATED"),
                anyString(), isNull(), anyMap(), anyMap());
    }

    @Test
    void updateReportStatusFailed_logsFailure() {
        UUID reportId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[] { reportId, EReportStatus.RESOLVED });

        aspect.updateReportStatusFailed(joinPoint, new RuntimeException("status update failed"));

        verify(auditLogService).logFailure(eq("REPORT"), eq(reportId.toString()), eq("REPORT_STATUS_UPDATED"),
                anyString(), eq("status update failed"), anyMap());
    }

    // ───────────────────────── NOTIFICATION / STATS ─────────────────────────

    @Test
    void afterSendNotification_logsAction() {
        NotificationEvent event = NotificationEvent.builder()
                .eventType("JOB_EXPIRED").recipientEmail("hr@test.com").referenceId("job-1").build();
        when(joinPoint.getArgs()).thenReturn(new Object[] { event });

        aspect.afterSendNotification(joinPoint);

        verify(auditLogService).logAction(eq("NOTIFICATION"), eq("job-1"), eq("NOTIFICATION_SENT"), anyString(),
                isNull(), anyMap(), anyMap());
    }

    @Test
    void afterSendNotificationFailed_logsFailure() {
        NotificationEvent event = NotificationEvent.builder()
                .eventType("JOB_EXPIRED").recipientEmail("hr@test.com").referenceId("job-1").build();
        when(joinPoint.getArgs()).thenReturn(new Object[] { event });

        aspect.afterSendNotificationFailed(joinPoint, new RuntimeException("notification failed"));

        verify(auditLogService).logFailure(eq("NOTIFICATION"), eq("job-1"), eq("NOTIFICATION_SENT"), anyString(),
                eq("notification failed"), anyMap());
    }

    @Test
    void afterUpdateJobStats_logsActionWithoutTransaction() {
        UUID jobId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[] { jobId, 5, "INCREMENT" });

        aspect.afterUpdateJobStats(joinPoint);

        verify(auditLogService).logAction(eq("JOB"), eq(jobId.toString()), eq("JOB_APPLICATION_STATS_UPDATED"),
                anyString(), isNull(), anyMap(), anyMap());
    }

    @Test
    void afterUpdateJobStats_logsActionAfterCommit_whenTransactionActive() {
        UUID jobId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[] { jobId, 5, "INCREMENT" });
        TransactionSynchronizationManager.initSynchronization();

        aspect.afterUpdateJobStats(joinPoint);
        org.springframework.transaction.support.TransactionSynchronizationUtils.triggerAfterCommit();
        TransactionSynchronizationManager.clearSynchronization();

        verify(auditLogService).logAction(eq("JOB"), eq(jobId.toString()), eq("JOB_APPLICATION_STATS_UPDATED"),
                anyString(), isNull(), anyMap(), anyMap());
    }

    @Test
    void logUpdateJobStatsFailed_logsFailure() {
        UUID jobId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[] { jobId, 5, "INCREMENT" });

        aspect.logUpdateJobStatsFailed(joinPoint, new RuntimeException("stats update failed"));

        verify(auditLogService).logFailure(eq("JOB"), eq(jobId.toString()), eq("JOB_APPLICATION_STATS_UPDATED"),
                anyString(), eq("stats update failed"), anyMap());
    }

    @Test
    void logUpdateJobStatsFailed_logsUnknown_whenJobIdNull() {
        when(joinPoint.getArgs()).thenReturn(new Object[] { null, 5, "INCREMENT" });

        aspect.logUpdateJobStatsFailed(joinPoint, new RuntimeException("stats update failed"));

        verify(auditLogService).logFailure(eq("JOB"), eq("unknown"), eq("JOB_APPLICATION_STATS_UPDATED"),
                anyString(), eq("stats update failed"), anyMap());
    }

    // ───────────────────────── RECOMMENDATION ─────────────────────────

    @Test
    void afterGetRecommendationsByCV_logsAction() {
        when(joinPoint.getArgs()).thenReturn(new Object[] { "alice", 5, null });
        ResJobRecommendationDTO result = ResJobRecommendationDTO.builder().totalResults(3).build();

        aspect.afterGetRecommendationsByCV(joinPoint, result);

        verify(auditLogService).logAction(eq("JOB_RECOMMENDATION"), eq("alice"), eq("CV_RECOMMENDATION_GENERATED"),
                anyString(), isNull(), anyMap(), anyMap());
    }

    @Test
    void afterGetRecommendationsByCV_defaultsTopK_whenTopKNull() {
        when(joinPoint.getArgs()).thenReturn(new Object[] { "alice", null, null });
        ResJobRecommendationDTO result = ResJobRecommendationDTO.builder().totalResults(3).build();

        aspect.afterGetRecommendationsByCV(joinPoint, result);

        verify(auditLogService).logAction(eq("JOB_RECOMMENDATION"), eq("alice"), eq("CV_RECOMMENDATION_GENERATED"),
                anyString(), isNull(), anyMap(), anyMap());
    }

    @Test
    void logGetRecommendationsByCVFailed_logsFailure() {
        when(joinPoint.getArgs()).thenReturn(new Object[] { "alice", 5, null });

        aspect.logGetRecommendationsByCVFailed(joinPoint, new RuntimeException("cv recommendation failed"));

        verify(auditLogService).logFailure(eq("JOB_RECOMMENDATION"), eq("alice"), eq("CV_RECOMMENDATION_GENERATED"),
                anyString(), eq("cv recommendation failed"), anyMap());
    }

    @Test
    void afterGetRecommendationsByProfile_logsAction() {
        ReqJobRecommendationDTO request = ReqJobRecommendationDTO.builder().profileText("x").topK(5).build();
        when(joinPoint.getArgs()).thenReturn(new Object[] { request });
        ResJobRecommendationDTO result = ResJobRecommendationDTO.builder().totalResults(2).build();

        aspect.afterGetRecommendationsByProfile(joinPoint, result);

        verify(auditLogService).logAction(eq("JOB_RECOMMENDATION"), eq("PROFILE"),
                eq("PROFILE_RECOMMENDATION_GENERATED"), anyString(), isNull(), anyMap(), anyMap());
    }

    @Test
    void logGetRecommendationsByProfileFailed_logsFailure() {
        aspect.logGetRecommendationsByProfileFailed(joinPoint, new RuntimeException("profile recommendation failed"));

        verify(auditLogService).logFailure(eq("JOB_RECOMMENDATION"), eq("PROFILE"),
                eq("PROFILE_RECOMMENDATION_GENERATED"), anyString(), eq("profile recommendation failed"), anyMap());
    }

    @Test
    void afterGetSimilarJobs_logsAction() {
        UUID jobId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[] { jobId, 10, false });
        ResJobRecommendationDTO result = ResJobRecommendationDTO.builder().totalResults(4).build();

        aspect.afterGetSimilarJobs(joinPoint, result);

        verify(auditLogService).logAction(eq("JOB"), eq(jobId.toString()), eq("SIMILAR_JOBS_GENERATED"),
                anyString(), isNull(), anyMap(), anyMap());
    }

    @Test
    void logGetSimilarJobsFailed_logsFailure() {
        UUID jobId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[] { jobId, 10, false });

        aspect.logGetSimilarJobsFailed(joinPoint, new RuntimeException("similar jobs failed"));

        verify(auditLogService).logFailure(eq("JOB"), eq(jobId.toString()), eq("SIMILAR_JOBS_GENERATED"),
                anyString(), eq("similar jobs failed"), anyMap());
    }

    @Test
    void afterGetSimilarJobs_defaultsTopK_whenTopKNull() {
        UUID jobId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[] { jobId, null, false });
        ResJobRecommendationDTO result = ResJobRecommendationDTO.builder().totalResults(4).build();

        aspect.afterGetSimilarJobs(joinPoint, result);

        verify(auditLogService).logAction(eq("JOB"), eq(jobId.toString()), eq("SIMILAR_JOBS_GENERATED"),
                anyString(), isNull(), anyMap(), anyMap());
    }

    @Test
    void logGetSimilarJobsFailed_logsUnknown_whenJobIdNull() {
        when(joinPoint.getArgs()).thenReturn(new Object[] { null, 10, false });

        aspect.logGetSimilarJobsFailed(joinPoint, new RuntimeException("similar jobs failed"));

        verify(auditLogService).logFailure(eq("JOB"), eq("unknown"), eq("SIMILAR_JOBS_GENERATED"),
                anyString(), eq("similar jobs failed"), anyMap());
    }
}
