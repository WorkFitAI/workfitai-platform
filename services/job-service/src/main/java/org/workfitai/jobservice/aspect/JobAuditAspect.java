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
import org.workfitai.jobservice.model.dto.AuditableResponse;
import org.workfitai.jobservice.model.dto.request.Skill.ReqUpdateSkillDTO;
import org.workfitai.jobservice.service.AuditLogService;

import java.util.HashMap;
import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class JobAuditAspect {

  private final AuditLogService auditLogService;

  // ───────────────────────── CREATE JOB ─────────────────────────

  @Pointcut("execution(* org.workfitai.jobservice.service.iJobService.createJob(..))")
  public void createJobPointcut() {
  }

  @AfterReturning(pointcut = "createJobPointcut()", returning = "result")
  public void afterCreateJob(JoinPoint jp, Object result) {

    String jobId = extractId(result);
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

  @Pointcut("execution(* org.workfitai.jobservice.service.iJobService.updateJob(..))")
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

  @Pointcut("execution(* org.workfitai.jobservice.service.iJobService.deleteJob(..))")
  public void deleteJobPointcut() {
  }

  @AfterReturning(pointcut = "deleteJobPointcut()")
  public void afterDeleteJob(JoinPoint jp) {

    String jobId = (String) jp.getArgs()[0];

    log.warn("No active transaction for jobId={}", jobId);
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

    String jobId = (String) jp.getArgs()[0];

    auditLogService.logFailure(
        "JOB",
        jobId,
        "JOB_SOFT_DELETED",
        currentUsername(),
        ex.getMessage(),
        metadata("Job soft delete failed"));
  }

  // ───────────────────────── UPDATE JOB STATUS ─────────────────────────

  @Pointcut("execution(* org.workfitai.jobservice.service.iJobService.updateStatus(..))")
  public void updateStatusJobPointcut() {
  }

  @AfterReturning(pointcut = "updateStatusJobPointcut()", returning = "result")
  public void afterUpdateStatus(JoinPoint jp, Object result) {

    String jobId = (String) jp.getArgs()[0]; // jobId param

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
  }

  @AfterThrowing(pointcut = "updateStatusJobPointcut()", throwing = "ex")
  public void logStatusUpdateFailed(JoinPoint jp, Throwable ex) {

    String jobId = (String) jp.getArgs()[0];

    auditLogService.logFailure(
        "JOB",
        jobId,
        "JOB_STATUS_UPDATED",
        currentUsername(),
        ex.getMessage(),
        metadata("Job status update failed"));
  }

  // ───────────────────────── CREATE COMPANY ─────────────────────────

  @Pointcut("execution(* org.workfitai.jobservice.service.iCompanyService.create(..))")
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

  @AfterThrowing("createCategoryPointcut()")
  public void createCategoryFailed(Throwable ex) {
    auditLogService.logFailure("JOB_CATEGORY", "unknown",
        "CATEGORY_CREATED",
        currentUsername(),
        ex.getMessage(),
        metadata("Category create failed"));
  }

  // ───────────────────────── UPDATE COMPANY ─────────────────────────
  @Pointcut("execution(* org.workfitai.jobservice.service.iCompanyService.update(..))")
  public void updateCompanyPointcut() {
  }

  @AfterReturning("updateCompanyPointcut()")
  public void afterUpdateCompany(JoinPoint jp) {

    String companyId = (String) jp.getArgs()[0];
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
    String companyId = (String) jp.getArgs()[0];

    auditLogService.logFailure(
        "COMPANY",
        companyId,
        "COMPANY_UPDATED",
        currentUsername(),
        ex.getMessage(),
        metadata("Company update failed"));
  }

  // ───────────────────────── DELETE COMPANY ─────────────────────────
  @Pointcut("execution(* org.workfitai.jobservice.service.iCompanyService.delete(..))")
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
  @Pointcut("execution(* org.workfitai.jobservice.service.iSkillService.create(..))")
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

  @AfterThrowing("createSkillPointcut()")
  public void createSkillFailed(Throwable ex) {
    auditLogService.logFailure("SKILL", "unknown",
        "SKILL_CREATED",
        currentUsername(),
        ex.getMessage(),
        metadata("Skill create failed"));
  }

  // ───────────────────────── UPDATE SKILL ─────────────────────────
  @Pointcut("execution(* org.workfitai.jobservice.service.iSkillService.update(..))")
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
  @Pointcut("execution(* org.workfitai.jobservice.service.iSkillService.delete(..))")
  public void deleteSkillPointcut() {
  }

  @AfterReturning("deleteSkillPointcut()")
  public void afterDeleteSkill(JoinPoint jp) {

    String skillId = (String) jp.getArgs()[0];

    auditLogService.logAction(
        "SKILL",
        skillId,
        "SKILL_DELETED",
        currentUsername(),
        null,
        Map.of("skillId", skillId),
        metadata("Skill deleted SUCCESS without transaction"));
  }

  @AfterThrowing("deleteSkillPointcut()")
  public void deleteSkillFailed(JoinPoint jp, Throwable ex) {
    String skillId = (String) jp.getArgs()[0];

    auditLogService.logFailure("SKILL", skillId,
        "SKILL_DELETED",
        currentUsername(),
        ex.getMessage(),
        metadata("Skill delete failed"));
  }

  // ───────────────────────── CREATE JOB CATEGORY ─────────────────────────

  @Pointcut("execution(* org.workfitai.jobservice.service.iJobCategoryService.create(..))")
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
  @Pointcut("execution(* org.workfitai.jobservice.service.iJobCategoryService.update(..))")
  public void updateCategoryPointcut() {
  }

  @AfterReturning("updateCategoryPointcut()")
  public void afterUpdateCategory(JoinPoint jp) {

    String categoryId = (String) jp.getArgs()[0];
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
              metadata("Category updated SUCCESS"));
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
          metadata("Category updated SUCCESS without transaction"));
    }
  }

  @AfterThrowing("updateCategoryPointcut()")
  public void updateCategoryFailed(JoinPoint jp, Throwable ex) {
    String categoryId = (String) jp.getArgs()[0];

    auditLogService.logFailure("JOB_CATEGORY", categoryId,
        "CATEGORY_UPDATED",
        currentUsername(),
        ex.getMessage(),
        metadata("Category update failed"));
  }

  // ───────────────────────── DELETE JOB CATEGORY ─────────────────────────
  @Pointcut("execution(* org.workfitai.jobservice.service.iJobCategoryService.delete(..))")
  public void deleteCategoryPointcut() {
  }

  @AfterReturning("deleteCategoryPointcut()")
  public void afterDeleteCategory(JoinPoint jp) {

    String categoryId = (String) jp.getArgs()[0];

    auditLogService.logAction(
        "JOB_CATEGORY",
        categoryId,
        "CATEGORY_DELETED",
        currentUsername(),
        null,
        Map.of("deleted", true),
        metadata("Category deleted SUCCESS without transaction"));
  }

  @AfterThrowing("deleteCategoryPointcut()")
  public void deleteCategoryFailed(JoinPoint jp, Throwable ex) {
    String categoryId = (String) jp.getArgs()[0];

    auditLogService.logFailure("JOB_CATEGORY", categoryId,
        "CATEGORY_DELETED",
        currentUsername(),
        ex.getMessage(),
        metadata("Category delete failed"));
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