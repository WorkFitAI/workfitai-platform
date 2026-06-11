package org.workfitai.jobservice.aspect;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.workfitai.jobservice.dto.kafka.NotificationEvent;
import org.workfitai.jobservice.model.dto.AuditableResponse;
import org.workfitai.jobservice.model.Job;
import org.workfitai.jobservice.model.dto.request.Recommendation.ReqJobRecommendationDTO;
import org.workfitai.jobservice.model.dto.request.Report.ReqCreateReport;
import org.workfitai.jobservice.model.dto.request.JobCategory.ReqUpdateJobCategoryDTO;
import org.workfitai.jobservice.model.dto.request.Skill.ReqUpdateSkillDTO;
import org.workfitai.jobservice.model.dto.response.Recommendation.ResJobRecommendationDTO;
import org.workfitai.jobservice.model.enums.EReportStatus;
import org.workfitai.jobservice.service.AuditLogService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class JobAuditAspect {

  private final AuditLogService auditLogService;

  // ───────────────────────── CREATE JOB ─────────────────────────

  @Pointcut("execution(* org.workfitai.jobservice.service.iJobService+.createJob(..))")
  public void createJobPointcut() {
  }

  @AfterReturning(pointcut = "createJobPointcut()", returning = "result")
  public void afterCreateJob(JoinPoint jp, Object result) {

    String jobId = extractId(result);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {

            @Override
            public void afterCommit() {
              auditLogService.logAction(
                  "JOB",
                  jobId,
                  "JOB_CREATED",
                  currentUsername(),
                  null,
                  Map.of("jobId", jobId),
                  metadata("Job created SUCCESS after commit"));
            }

            @Override
            public void afterCompletion(int status) {
              if (status == STATUS_ROLLED_BACK) {
                log.warn("CREATE rolled back jobId={}", jobId);
              }
            }
          });
    } else {
      log.warn("No active transaction for jobId={}", jobId);
      auditLogService.logAction(
          "JOB",
          jobId,
          "JOB_CREATED",
          currentUsername(),
          null,
          Map.of("jobId", jobId),
          metadata("Job created SUCCESS without transaction"));
    }
  }

  @AfterThrowing(pointcut = "createJobPointcut()", throwing = "ex")
  public void logJobCreateFailed(JoinPoint jp, Throwable ex) {
    auditLogService.logFailure(
        "JOB",
        "unknown",
        "JOB_CREATED",
        currentUsername(),
        ex.getMessage(),
        metadata("Job creation failed"));
  }

  // ───────────────────────── UPDATE JOB ─────────────────────────

  @Pointcut("execution(* org.workfitai.jobservice.service.iJobService+.updateJob(..))")
  public void updateJobPointcut() {
  }

  @AfterReturning(pointcut = "updateJobPointcut()", returning = "result")
  public void afterUpdateJob(JoinPoint jp, Object result) {

    String jobId = extractId(result);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {

            @Override
            public void afterCommit() {
              auditLogService.logAction(
                  "JOB",
                  jobId,
                  "JOB_UPDATED",
                  currentUsername(),
                  null,
                  Map.of("jobId", jobId),
                  metadata("Job updated SUCCESS after commit"));
            }
          });
    } else {
      log.warn("No active transaction for jobId={}", jobId);
      auditLogService.logAction(
          "JOB",
          jobId,
          "JOB_UPDATED",
          currentUsername(),
          null,
          Map.of("jobId", jobId),
          metadata("Job updated SUCCESS without transaction"));
    }
  }

  @AfterThrowing(pointcut = "updateJobPointcut()", throwing = "ex")
  public void logJobUpdateFailed(JoinPoint jp, Throwable ex) {
    auditLogService.logFailure(
        "JOB",
        "unknown",
        "JOB_UPDATED",
        currentUsername(),
        ex.getMessage(),
        metadata("Job update failed"));
  }

  // ───────────────────────── DELETE JOB (SOFT DELETE) ─────────────────────────

  @Pointcut("execution(* org.workfitai.jobservice.service.iJobService+.deleteJob(..))")
  public void deleteJobPointcut() {
  }

  @AfterReturning(pointcut = "deleteJobPointcut()")
  public void afterDeleteJob(JoinPoint jp) {

    String jobId = jp.getArgs()[0].toString();

    log.info("Job soft deleted, jobId={}", jobId);
    auditLogService.logAction(
        "JOB",
        jobId,
        "JOB_SOFT_DELETED",
        currentUsername(),
        null,
        Map.of("deleted", true),
        metadata("Job soft deleted SUCCESS without transaction"));
  }

  @AfterThrowing(pointcut = "deleteJobPointcut()", throwing = "ex")
  public void logJobDeleteFailed(JoinPoint jp, Throwable ex) {

    String jobId = jp.getArgs()[0].toString();

    auditLogService.logFailure(
        "JOB",
        jobId,
        "JOB_SOFT_DELETED",
        currentUsername(),
        ex.getMessage(),
        metadata("Job soft delete failed"));
  }

  // ───────────────────────── UPDATE JOB STATUS ─────────────────────────

  @Pointcut("execution(* org.workfitai.jobservice.service.iJobService+.updateStatus(..))")
  public void updateStatusJobPointcut() {
  }

  @AfterReturning(pointcut = "updateStatusJobPointcut()", returning = "result")
  public void afterUpdateStatus(JoinPoint jp, Object result) {

    String jobId = ((Job) jp.getArgs()[0]).getJobId().toString();

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {

            @Override
            public void afterCommit() {
              auditLogService.logAction(
                  "JOB",
                  jobId,
                  "JOB_STATUS_UPDATED",
                  currentUsername(),
                  null,
                  Map.of("jobId", jobId),
                  metadata("Job status updated SUCCESS"));
            }
          });
    } else {
      log.warn("No active transaction for jobId={}", jobId);
      auditLogService.logAction(
          "JOB",
          jobId,
          "JOB_STATUS_UPDATED",
          currentUsername(),
          null,
          Map.of("jobId", jobId),
          metadata("Job status updated SUCCESS without transaction"));
    }
  }

  @AfterThrowing(pointcut = "updateStatusJobPointcut()", throwing = "ex")
  public void logStatusUpdateFailed(JoinPoint jp, Throwable ex) {

    String jobId = ((Job) jp.getArgs()[0]).getJobId().toString();

    auditLogService.logFailure(
        "JOB",
        jobId,
        "JOB_STATUS_UPDATED",
        currentUsername(),
        ex.getMessage(),
        metadata("Job status update failed"));
  }

  // ───────────────────────── CREATE COMPANY ─────────────────────────

  @Pointcut("execution(* org.workfitai.jobservice.service.iCompanyService+.create(..))")
  public void createCompanyPointcut() {
  }

  @AfterReturning(pointcut = "createCompanyPointcut()", returning = "result")
  public void afterCreateCompany(JoinPoint jp, Object result) {

    String companyId = extractId(result);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              auditLogService.logAction(
                  "COMPANY",
                  companyId,
                  "COMPANY_CREATED",
                  currentUsername(),
                  null,
                  Map.of("companyId", companyId),
                  metadata("Company created SUCCESS after commit"));
            }
          });
    } else {
      log.warn("No active transaction for companyId={}", companyId);
      auditLogService.logAction(
          "COMPANY",
          companyId,
          "COMPANY_CREATED",
          currentUsername(),
          null,
          Map.of("companyId", companyId),
          metadata("Company created SUCCESS without transaction"));
    }
  }

  // ───────────────────────── UPDATE COMPANY ─────────────────────────
  @Pointcut("execution(* org.workfitai.jobservice.service.iCompanyService+.update(..))")
  public void updateCompanyPointcut() {
  }

  @AfterReturning(pointcut = "updateCompanyPointcut()", returning = "result")
  public void afterUpdateCompany(JoinPoint jp, Object result) {

    String companyId = extractId(result);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          auditLogService.logAction(
              "COMPANY",
              companyId,
              "COMPANY_UPDATED",
              currentUsername(),
              null,
              Map.of("companyId", companyId),
              metadata("Company updated SUCCESS"));
        }
      });
    } else {
      log.warn("No active transaction for companyId={}", companyId);
      auditLogService.logAction(
          "COMPANY",
          companyId,
          "COMPANY_UPDATED",
          currentUsername(),
          null,
          Map.of("companyId", companyId),
          metadata("Company updated SUCCESS without transaction"));
    }
  }

  @AfterThrowing(pointcut = "updateCompanyPointcut()", throwing = "ex")
  public void updateCompanyFailed(JoinPoint jp, Throwable ex) {
    String companyId = "unknown";

    auditLogService.logFailure(
        "COMPANY",
        companyId,
        "COMPANY_UPDATED",
        currentUsername(),
        ex.getMessage(),
        metadata("Company update failed"));
  }

  // ───────────────────────── DELETE COMPANY ─────────────────────────
  @Pointcut("execution(* org.workfitai.jobservice.service.iCompanyService+.delete(..))")
  public void deleteCompanyPointcut() {
  }

  @AfterReturning("deleteCompanyPointcut()")
  public void afterDeleteCompany(JoinPoint jp) {

    String companyId = (String) jp.getArgs()[0];
    auditLogService.logAction(
        "COMPANY",
        companyId,
        "COMPANY_DELETED",
        currentUsername(),
        null,
        Map.of("deleted", true),
        metadata("Company deleted SUCCESS without transaction"));
  }

  @AfterThrowing(pointcut = "deleteCompanyPointcut()", throwing = "ex")
  public void deleteCompanyFailed(JoinPoint jp, Throwable ex) {
    String companyId = (String) jp.getArgs()[0];

    auditLogService.logFailure(
        "COMPANY",
        companyId,
        "COMPANY_DELETED",
        currentUsername(),
        ex.getMessage(),
        metadata("Company delete failed"));
  }

  // ───────────────────────── CREATE SKILL ─────────────────────────
  @Pointcut("execution(* org.workfitai.jobservice.service.iSkillService+.create(..))")
  public void createSkillPointcut() {
  }

  @AfterReturning(pointcut = "createSkillPointcut()", returning = "result")
  public void afterCreateSkill(JoinPoint jp, Object result) {

    String skillId = extractId(result);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              auditLogService.logAction(
                  "SKILL",
                  skillId,
                  "SKILL_CREATED",
                  currentUsername(),
                  null,
                  Map.of("skillId", skillId),
                  metadata("Skill created SUCCESS"));
            }
          });
    } else {
      log.warn("No active transaction for skillId={}", skillId);
      auditLogService.logAction(
          "SKILL",
          skillId,
          "SKILL_CREATED",
          currentUsername(),
          null,
          Map.of("skillId", skillId),
          metadata("Skill created SUCCESS without transaction"));
    }
  }

  // ───────────────────────── UPDATE SKILL ─────────────────────────
  @Pointcut("execution(* org.workfitai.jobservice.service.iSkillService+.update(..))")
  public void updateSkillPointcut() {
  }

  @AfterReturning("updateSkillPointcut()")
  public void afterUpdateSkill(JoinPoint jp) {

    ReqUpdateSkillDTO req = (ReqUpdateSkillDTO) jp.getArgs()[0];
    String skillId = req.getSkillId().toString();
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              auditLogService.logAction(
                  "SKILL",
                  skillId,
                  "SKILL_UPDATED",
                  currentUsername(),
                  null,
                  Map.of("skillId", skillId),
                  metadata("Skill updated SUCCESS"));
            }

            @Override
            public void afterCompletion(int status) {
              if (status == STATUS_ROLLED_BACK) {
                log.warn("Transaction rolled back");
              }
            }
          });
    } else {
      log.warn("No active transaction for skillId={}", skillId);
      auditLogService.logAction(
          "SKILL",
          skillId,
          "SKILL_UPDATED",
          currentUsername(),
          null,
          Map.of("skillId", skillId),
          metadata("Skill updated SUCCESS without transaction"));
    }
  }

  @AfterThrowing(pointcut = "updateSkillPointcut()", throwing = "ex")
  public void updateSkillFailed(JoinPoint jp, Throwable ex) {
    ReqUpdateSkillDTO req = (ReqUpdateSkillDTO) jp.getArgs()[0];
    String skillId = req.getSkillId().toString();

    auditLogService.logFailure("SKILL", skillId,
        "SKILL_UPDATED",
        currentUsername(),
        ex.getMessage(),
        metadata("Skill update failed"));
  }

  // ───────────────────────── DELETE SKILL ─────────────────────────
  @Pointcut("execution(* org.workfitai.jobservice.service.iSkillService+.delete(..))")
  public void deleteSkillPointcut() {
  }

  @AfterReturning("deleteSkillPointcut()")
  public void afterDeleteSkill(JoinPoint jp) {

    String skillId = jp.getArgs()[0].toString();

    auditLogService.logAction(
        "SKILL",
        skillId,
        "SKILL_DELETED",
        currentUsername(),
        null,
        Map.of("skillId", skillId),
        metadata("Skill deleted SUCCESS without transaction"));
  }

  @AfterThrowing(pointcut = "deleteSkillPointcut()", throwing = "ex")
  public void deleteSkillFailed(JoinPoint jp, Throwable ex) {
    String skillId = jp.getArgs()[0].toString();

    auditLogService.logFailure("SKILL", skillId,
        "SKILL_DELETED",
        currentUsername(),
        ex.getMessage(),
        metadata("Skill delete failed"));
  }

  // ───────────────────────── CREATE JOB CATEGORY ─────────────────────────

  @Pointcut("execution(* org.workfitai.jobservice.service.iJobCategoryService+.create(..))")
  public void createJobCategoryPointcut() {
  }

  @AfterReturning(pointcut = "createJobCategoryPointcut()", returning = "result")
  public void afterCreateJobCategory(JoinPoint jp, Object result) {

    String categoryId = extractId(result);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {

      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          auditLogService.logAction(
              "JOB_CATEGORY",
              categoryId,
              "JOB_CATEGORY_CREATED",
              currentUsername(),
              null,
              Map.of("categoryId", categoryId),
              metadata("Job category created SUCCESS"));
        }
      });
    } else {
      log.warn("No active transaction for categoryId={}", categoryId);
      auditLogService.logAction(
          "JOB_CATEGORY",
          categoryId,
          "JOB_CATEGORY_CREATED",
          currentUsername(),
          null,
          Map.of("categoryId", categoryId),
          metadata("Job category created SUCCESS without transaction"));
    }
  }

  @AfterThrowing(pointcut = "createJobCategoryPointcut()", throwing = "ex")
  public void logJobCategoryCreateFailed(JoinPoint jp, Throwable ex) {
    auditLogService.logFailure(
        "JOB_CATEGORY",
        "unknown",
        "JOB_CATEGORY_CREATED",
        currentUsername(),
        ex.getMessage(),
        metadata("Job category creation failed"));
  }

  @AfterThrowing(pointcut = "createSkillPointcut()", throwing = "ex")
  public void logSkillCreateFailed(JoinPoint jp, Throwable ex) {
    auditLogService.logFailure(
        "SKILL",
        "unknown",
        "SKILL_CREATED",
        currentUsername(),
        ex.getMessage(),
        metadata("Skill creation failed"));
  }

  // ───────────────────────── UPDATE JOB CATEGORY ─────────────────────────
  @Pointcut("execution(* org.workfitai.jobservice.service.iJobCategoryService+.update(..))")
  public void updateCategoryPointcut() {
  }

  @AfterReturning(pointcut = "updateCategoryPointcut()", returning = "result")
  public void afterUpdateCategory(JoinPoint jp, Object result) {

    String categoryId = extractId(result);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          auditLogService.logAction(
              "JOB_CATEGORY",
              categoryId,
              "CATEGORY_UPDATED",
              currentUsername(),
              null,
              Map.of("categoryId", categoryId),
              metadata("Job category updated SUCCESS"));
        }
      });
    } else {
      log.warn("No active transaction for categoryId={}", categoryId);
      auditLogService.logAction(
          "JOB_CATEGORY",
          categoryId,
          "CATEGORY_UPDATED",
          currentUsername(),
          null,
          Map.of("categoryId", categoryId),
          metadata("Job category updated SUCCESS without transaction"));
    }
  }

  @AfterThrowing(pointcut = "updateCategoryPointcut()", throwing = "ex")
  public void updateCategoryFailed(JoinPoint jp, Throwable ex) {
    ReqUpdateJobCategoryDTO dto = (ReqUpdateJobCategoryDTO) jp.getArgs()[0];
    String categoryId = dto.getId() != null ? dto.getId().toString() : "unknown";

    auditLogService.logFailure("JOB_CATEGORY", categoryId,
        "CATEGORY_UPDATED",
        currentUsername(),
        ex.getMessage(),
        metadata("Category update failed"));
  }

  // ───────────────────────── DELETE JOB CATEGORY ─────────────────────────
  @Pointcut("execution(* org.workfitai.jobservice.service.iJobCategoryService+.delete(..))")
  public void deleteCategoryPointcut() {
  }

  @AfterReturning("deleteCategoryPointcut()")
  public void afterDeleteCategory(JoinPoint jp) {

    String categoryId = jp.getArgs()[0].toString();

    auditLogService.logAction(
        "JOB_CATEGORY",
        categoryId,
        "CATEGORY_DELETED",
        currentUsername(),
        null,
        Map.of("deleted", true),
        metadata("Category deleted SUCCESS without transaction"));
  }

  @AfterThrowing(pointcut = "deleteCategoryPointcut()", throwing = "ex")
  public void deleteCategoryFailed(JoinPoint jp, Throwable ex) {
    String categoryId = jp.getArgs()[0].toString();

    auditLogService.logFailure("JOB_CATEGORY", categoryId,
        "CATEGORY_DELETED",
        currentUsername(),
        ex.getMessage(),
        metadata("Category delete failed"));
  }

  // ───────────────────────── CREATE REPORT ─────────────────────────

  @Pointcut("execution(* org.workfitai.jobservice.service.iReportService+.createReport(..))")
  public void createReportPointcut() {
  }

  @AfterReturning(pointcut = "createReportPointcut()", returning = "result")
  public void afterCreateReport(
      JoinPoint jp,
      Object result) {

    String jobId = ((ReqCreateReport) jp.getArgs()[0]).getJobId().toString();

    auditLogService.logAction(
        "REPORT",
        jobId,
        "REPORT_CREATED",
        currentUsername(),
        null,
        Map.of(
            "result", result,
            "jobId", jobId),
        metadata("Report created successfully"));
  }

  @AfterThrowing(pointcut = "createReportPointcut()", throwing = "ex")
  public void createReportFailed(
      JoinPoint jp,
      Throwable ex) {

    String jobId = null;

    if (jp.getArgs().length > 0
        && jp.getArgs()[0] instanceof ReqCreateReport req) {
      jobId = req.getJobId().toString();
    }

    auditLogService.logFailure(
        "REPORT",
        jobId != null
            ? jobId
            : "unknown",
        "REPORT_CREATED",
        currentUsername(),
        ex.getMessage(),
        metadata("Report creation failed"));
  }

  // ───────────────────────── UPDATE STATUS REPORT ─────────────────────────
  @Pointcut("execution(* org.workfitai.jobservice.service.iReportService+.changeStatus(..))")
  public void updateReportStatusPointcut() {
  }

  @AfterReturning("updateReportStatusPointcut()")
  public void afterUpdateReportStatus(JoinPoint jp) {

    UUID reportId = (UUID) jp.getArgs()[0];
    EReportStatus newStatus = (EReportStatus) jp.getArgs()[1];

    if (TransactionSynchronizationManager.isSynchronizationActive()) {

      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {

            @Override
            public void afterCommit() {

              auditLogService.logAction(
                  "REPORT",
                  reportId.toString(),
                  "REPORT_STATUS_UPDATED",
                  currentUsername(),
                  null,
                  Map.of(
                      "reportId", reportId,
                      "newStatus", newStatus),
                  metadata("Report status updated SUCCESS"));
            }
          });

    } else {

      log.warn("No active transaction for reportId={}", reportId);

      auditLogService.logAction(
          "REPORT",
          reportId.toString(),
          "REPORT_STATUS_UPDATED",
          currentUsername(),
          null,
          Map.of(
              "reportId", reportId,
              "newStatus", newStatus),
          metadata("Report status updated SUCCESS without transaction"));
    }
  }

  @AfterThrowing(pointcut = "updateReportStatusPointcut()", throwing = "ex")
  public void updateReportStatusFailed(
      JoinPoint jp,
      Throwable ex) {

    UUID reportId = (UUID) jp.getArgs()[0];

    auditLogService.logFailure(
        "REPORT",
        reportId.toString(),
        "REPORT_STATUS_UPDATED",
        currentUsername(),
        ex.getMessage(),
        metadata("Report status update failed"));
  }

  // ───────────────────────── Notification Sent ─────────────────────────

  @Pointcut("execution(* org.workfitai.jobservice.service.impl.JobService.sendJobCreatedNotification(..))")
  public void sendNotificationPointcut() {
  }

  @AfterReturning("sendNotificationPointcut()")
  public void afterSendNotification(JoinPoint jp) {

    NotificationEvent event = (NotificationEvent) jp.getArgs()[0];

    auditLogService.logAction(
        "NOTIFICATION",
        event.getReferenceId(),
        "NOTIFICATION_SENT",
        currentUsername(),
        null,
        Map.of(
            "eventType", event.getEventType(),
            "recipient", event.getRecipientEmail()),
        metadata("Notification sent successfully"));
  }

  @AfterThrowing(pointcut = "sendNotificationPointcut()", throwing = "ex")
  public void afterSendNotificationFailed(
      JoinPoint jp,
      Throwable ex) {

    NotificationEvent event = (NotificationEvent) jp.getArgs()[0];

    auditLogService.logFailure(
        "NOTIFICATION",
        event.getReferenceId(),
        "NOTIFICATION_SENT",
        currentUsername(),
        ex.getMessage(),
        metadata("Notification send failed"));
  }

  @Pointcut("execution(* org.workfitai.jobservice.service.impl.JobService.updateStats(..))")
  public void updateJobStatsPointcut() {
  }

  @AfterReturning("updateJobStatsPointcut()")
  public void afterUpdateJobStats(JoinPoint jp) {

    UUID jobId = (UUID) jp.getArgs()[0];
    int applyCount = (Integer) jp.getArgs()[1];
    String operation = (String) jp.getArgs()[2];

    if (TransactionSynchronizationManager.isSynchronizationActive()) {

      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              auditLogService.logAction(
                  "JOB",
                  jobId.toString(),
                  "JOB_APPLICATION_STATS_UPDATED",
                  currentUsername(),
                  null,
                  Map.of(
                      "jobId", jobId.toString(),
                      "applyCount", applyCount,
                      "operation", operation),
                  metadata("Job application stats updated successfully"));
            }
          });

    } else {

      auditLogService.logAction(
          "JOB",
          jobId.toString(),
          "JOB_APPLICATION_STATS_UPDATED",
          currentUsername(),
          null,
          Map.of(
              "jobId", jobId.toString(),
              "applyCount", applyCount,
              "operation", operation),
          metadata("Job application stats updated successfully without transaction"));
    }
  }

  @AfterThrowing(pointcut = "updateJobStatsPointcut()", throwing = "ex")
  public void logUpdateJobStatsFailed(
      JoinPoint jp,
      Throwable ex) {

    UUID jobId = (UUID) jp.getArgs()[0];

    auditLogService.logFailure(
        "JOB",
        jobId != null ? jobId.toString() : "unknown",
        "JOB_APPLICATION_STATS_UPDATED",
        currentUsername(),
        ex.getMessage(),
        metadata("Job application stats update failed"));
  }

  // ───────────────────────── Recommendation ─────────────────────────
  @Pointcut("execution(* org.workfitai.jobservice.service.iRecommendationService+.getRecommendationsByCV(..))")
  public void getRecommendationsByCVPointcut() {
  }

  @AfterReturning(pointcut = "getRecommendationsByCVPointcut()", returning = "result")
  public void afterGetRecommendationsByCV(
      JoinPoint jp,
      Object result) {

    String userId = (String) jp.getArgs()[0];
    Integer topK = (Integer) jp.getArgs()[1];

    ResJobRecommendationDTO response = (ResJobRecommendationDTO) result;

    auditLogService.logAction(
        "JOB_RECOMMENDATION",
        userId,
        "CV_RECOMMENDATION_GENERATED",
        currentUsername(),
        null,
        Map.of(
            "userId", userId,
            "topK", topK != null ? topK : 20,
            "totalResults", response.getTotalResults()),
        metadata("CV recommendation generated successfully"));
  }

  @AfterThrowing(pointcut = "getRecommendationsByCVPointcut()", throwing = "ex")
  public void logGetRecommendationsByCVFailed(
      JoinPoint jp,
      Throwable ex) {

    String userId = (String) jp.getArgs()[0];

    auditLogService.logFailure(
        "JOB_RECOMMENDATION",
        userId,
        "CV_RECOMMENDATION_GENERATED",
        currentUsername(),
        ex.getMessage(),
        metadata("CV recommendation generation failed"));
  }

  @Pointcut("execution(* org.workfitai.jobservice.service.iRecommendationService+.getRecommendationsByProfile(..))")
  public void getRecommendationsByProfilePointcut() {
  }

  @AfterReturning(pointcut = "getRecommendationsByProfilePointcut()", returning = "result")
  public void afterGetRecommendationsByProfile(
      JoinPoint jp,
      Object result) {

    ReqJobRecommendationDTO request = (ReqJobRecommendationDTO) jp.getArgs()[0];

    ResJobRecommendationDTO response = (ResJobRecommendationDTO) result;

    auditLogService.logAction(
        "JOB_RECOMMENDATION",
        "PROFILE",
        "PROFILE_RECOMMENDATION_GENERATED",
        currentUsername(),
        null,
        Map.of(
            "topK", request.getTopK(),
            "totalResults", response.getTotalResults()),
        metadata("Profile recommendation generated successfully"));
  }

  @AfterThrowing(pointcut = "getRecommendationsByProfilePointcut()", throwing = "ex")
  public void logGetRecommendationsByProfileFailed(
      JoinPoint jp,
      Throwable ex) {

    auditLogService.logFailure(
        "JOB_RECOMMENDATION",
        "PROFILE",
        "PROFILE_RECOMMENDATION_GENERATED",
        currentUsername(),
        ex.getMessage(),
        metadata("Profile recommendation generation failed"));
  }

  @Pointcut("execution(* org.workfitai.jobservice.service.iRecommendationService+.getSimilarJobs(..))")
  public void getSimilarJobsPointcut() {
  }

  @AfterReturning(pointcut = "getSimilarJobsPointcut()", returning = "result")
  public void afterGetSimilarJobs(
      JoinPoint jp,
      Object result) {

    UUID jobId = (UUID) jp.getArgs()[0];
    Integer topK = (Integer) jp.getArgs()[1];
    Boolean excludeSameCompany = (Boolean) jp.getArgs()[2];

    ResJobRecommendationDTO response = (ResJobRecommendationDTO) result;

    auditLogService.logAction(
        "JOB",
        jobId.toString(),
        "SIMILAR_JOBS_GENERATED",
        currentUsername(),
        null,
        Map.of(
            "jobId", jobId.toString(),
            "topK", topK != null ? topK : 10,
            "excludeSameCompany", excludeSameCompany,
            "totalResults", response.getTotalResults()),
        metadata("Similar jobs generated successfully"));
  }

  @AfterThrowing(pointcut = "getSimilarJobsPointcut()", throwing = "ex")
  public void logGetSimilarJobsFailed(
      JoinPoint jp,
      Throwable ex) {

    UUID jobId = (UUID) jp.getArgs()[0];

    auditLogService.logFailure(
        "JOB",
        jobId != null ? jobId.toString() : "unknown",
        "SIMILAR_JOBS_GENERATED",
        currentUsername(),
        ex.getMessage(),
        metadata("Similar jobs generation failed"));
  }

  // ───────────────────────── HELPERS ─────────────────────────

  private String currentUsername() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return (auth != null && auth.isAuthenticated()) ? auth.getName() : "SYSTEM";
  }

  private Map<String, Object> metadata(String description) {
    Map<String, Object> m = new HashMap<>();
    m.put("description", description);
    m.put("timestamp", System.currentTimeMillis());
    return m;
  }

  private String extractId(Object result) {
    if (result instanceof AuditableResponse dto) {
      return dto.getAuditId();
    }
    return "unknown";
  }

}
