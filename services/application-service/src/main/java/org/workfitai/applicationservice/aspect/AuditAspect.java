package org.workfitai.applicationservice.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.workfitai.applicationservice.dto.request.CreateApplicationRequest;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.dto.response.BulkUpdateResult;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;
import org.workfitai.applicationservice.service.AuditLogService;

import java.util.HashMap;
import java.util.Map;

/**
 * AOP aspect for application audit logging.
 * Intercepts service methods to write audit trail entries on both success and failure paths.
 * Action names follow the APPLICATION_* taxonomy for consistent Elasticsearch queries.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogService auditLogService;
    private final ApplicationRepository applicationRepository;

    // Captures the application's current status BEFORE updateStatus executes.
    // ThreadLocal ensures isolation across concurrent requests.
    private static final ThreadLocal<ApplicationStatus> previousStatusHolder = new ThreadLocal<>();

    // ─── Pointcuts ────────────────────────────────────────────────────────────

    @Pointcut("execution(* org.workfitai.applicationservice.saga.ApplicationSagaOrchestrator.createApplication(..))")
    public void createApplicationPointcut() {}

    @Pointcut("execution(* org.workfitai.applicationservice.service.impl.ApplicationServiceImpl.updateStatus(..))")
    public void updateStatusPointcut() {}

    @Pointcut("execution(* org.workfitai.applicationservice.service.impl.ApplicationServiceImpl.withdrawApplication(..))")
    public void withdrawApplicationPointcut() {}

    @Pointcut("execution(* org.workfitai.applicationservice.service.ApplicationNoteService.addNote(..))")
    public void addNotePointcut() {}

    @Pointcut("execution(* org.workfitai.applicationservice.service.ApplicationNoteService.updateNote(..))")
    public void updateNotePointcut() {}

    @Pointcut("execution(* org.workfitai.applicationservice.service.ApplicationNoteService.deleteNote(..))")
    public void deleteNotePointcut() {}

    @Pointcut("execution(* org.workfitai.applicationservice.service.AssignmentService.assignApplication(..))")
    public void assignApplicationPointcut() {}

    @Pointcut("execution(* org.workfitai.applicationservice.service.AssignmentService.unassignApplication(..))")
    public void unassignApplicationPointcut() {}

    @Pointcut("execution(* org.workfitai.applicationservice.service.BulkOperationService.bulkUpdateStatus(..))")
    public void bulkUpdateStatusPointcut() {}

    @Pointcut("execution(* org.workfitai.applicationservice.service.ExportService.exportApplications(..))")
    public void exportApplicationsPointcut() {}

    @Pointcut("execution(* org.workfitai.applicationservice.service.ExportService.exportAllApplications(..))")
    public void exportAllApplicationsPointcut() {}

    @Pointcut("execution(* org.workfitai.applicationservice.service.AdminApplicationService.createApplication(..))")
    public void adminCreatePointcut() {}

    @Pointcut("execution(* org.workfitai.applicationservice.service.AdminApplicationService.overrideApplication(..))")
    public void adminOverridePointcut() {}

    @Pointcut("execution(* org.workfitai.applicationservice.service.AdminApplicationService.restoreApplication(..))")
    public void adminRestorePointcut() {}

    @Pointcut("execution(* org.workfitai.applicationservice.service.AdminApplicationService.permanentlyDeleteApplication(..))")
    public void adminDeletePointcut() {}

    @Pointcut("execution(* org.workfitai.applicationservice.service.AdminApplicationService.softDeleteApplication(..))")
    public void adminSoftDeletePointcut() {}

    @Pointcut("execution(* org.workfitai.applicationservice.controller.HRApplicationController.downloadCv(..))")
    public void downloadCvPointcut() {}

    // ─── Capture previous state ───────────────────────────────────────────────

    @Before("updateStatusPointcut()")
    public void capturePreviousStatus(JoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args.length > 0 && args[0] instanceof String applicationId) {
                applicationRepository.findByIdAndDeletedAtIsNull(applicationId)
                        .ifPresent(app -> previousStatusHolder.set(app.getStatus()));
            }
        } catch (Exception e) {
            log.error("Error capturing previous status for audit", e);
        }
    }

    // ─── APPLICATION_SUBMITTED ────────────────────────────────────────────────

    @AfterReturning(pointcut = "createApplicationPointcut()", returning = "result")
    public void logApplicationSubmitted(JoinPoint joinPoint, Object result) {
        try {
            if (result instanceof ApplicationResponse response) {
                Map<String, Object> afterState = new HashMap<>();
                afterState.put("applicationId", response.getId());
                afterState.put("jobId", response.getJobId());
                afterState.put("status", response.getStatus());
                afterState.put("username", response.getUsername());

                auditLogService.logAction(
                        "APPLICATION", response.getId(),
                        "APPLICATION_SUBMITTED",
                        getCurrentUsername(),
                        null, afterState,
                        buildMetadata("Candidate submitted application for job " + response.getJobId()));
            }
        } catch (Exception e) {
            log.error("Error logging APPLICATION_SUBMITTED", e);
        }
    }

    @AfterThrowing(pointcut = "createApplicationPointcut()", throwing = "ex")
    public void logApplicationSubmitFailed(JoinPoint joinPoint, Throwable ex) {
        try {
            Object[] args = joinPoint.getArgs();
            String jobId = extractJobIdFromArgs(args);
            auditLogService.logFailure(
                    "APPLICATION", jobId != null ? jobId : "unknown",
                    "APPLICATION_SUBMITTED",
                    getCurrentUsername(),
                    ex.getMessage(),
                    buildMetadata("Application submission failed"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_SUBMITTED failure", e);
        }
    }

    // ─── APPLICATION_STATUS_CHANGED ──────────────────────────────────────────

    @AfterReturning(pointcut = "updateStatusPointcut()", returning = "result")
    public void logStatusChanged(JoinPoint joinPoint, Object result) {
        try {
            Object[] args = joinPoint.getArgs();
            String applicationId = (String) args[0];
            ApplicationStatus previousStatus = previousStatusHolder.get();

            if (result instanceof ApplicationResponse response) {
                Map<String, Object> beforeState = previousStatus != null
                        ? Map.of("status", previousStatus) : null;
                Map<String, Object> afterState = new HashMap<>();
                afterState.put("status", response.getStatus());
                afterState.put("updatedAt", response.getUpdatedAt());

                auditLogService.logAction(
                        "APPLICATION", applicationId,
                        "APPLICATION_STATUS_CHANGED",
                        getCurrentUsername(),
                        beforeState, afterState,
                        buildMetadata("Status changed from " + previousStatus + " to " + response.getStatus()));
            }
        } catch (Exception e) {
            log.error("Error logging APPLICATION_STATUS_CHANGED", e);
        } finally {
            previousStatusHolder.remove();
        }
    }

    @AfterThrowing(pointcut = "updateStatusPointcut()", throwing = "ex")
    public void logStatusChangeFailed(JoinPoint joinPoint, Throwable ex) {
        try {
            previousStatusHolder.remove();
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logFailure(
                    "APPLICATION", applicationId,
                    "APPLICATION_STATUS_CHANGED",
                    getCurrentUsername(),
                    ex.getMessage(),
                    buildMetadata("Status update failed"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_STATUS_CHANGED failure", e);
        }
    }

    // ─── APPLICATION_WITHDRAWN ────────────────────────────────────────────────

    @AfterReturning(pointcut = "withdrawApplicationPointcut()")
    public void logApplicationWithdrawn(JoinPoint joinPoint) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            Map<String, Object> afterState = Map.of("status", "WITHDRAWN", "withdrawnAt", System.currentTimeMillis());
            auditLogService.logAction(
                    "APPLICATION", applicationId,
                    "APPLICATION_WITHDRAWN",
                    getCurrentUsername(),
                    null, afterState,
                    buildMetadata("Candidate withdrew their application"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_WITHDRAWN", e);
        }
    }

    @AfterThrowing(pointcut = "withdrawApplicationPointcut()", throwing = "ex")
    public void logWithdrawalFailed(JoinPoint joinPoint, Throwable ex) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logFailure(
                    "APPLICATION", applicationId,
                    "APPLICATION_WITHDRAWN",
                    getCurrentUsername(),
                    ex.getMessage(),
                    buildMetadata("Application withdrawal failed"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_WITHDRAWN failure", e);
        }
    }

    // ─── APPLICATION_NOTE_ADDED ───────────────────────────────────────────────

    @AfterReturning(pointcut = "addNotePointcut()", returning = "result")
    public void logNoteAdded(JoinPoint joinPoint, Object result) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logAction(
                    "APPLICATION_NOTE", applicationId,
                    "APPLICATION_NOTE_ADDED",
                    getCurrentUsername(),
                    null, null,
                    buildMetadata("HR added a note to application"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_NOTE_ADDED", e);
        }
    }

    @AfterThrowing(pointcut = "addNotePointcut()", throwing = "ex")
    public void logNoteAddFailed(JoinPoint joinPoint, Throwable ex) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logFailure(
                    "APPLICATION_NOTE", applicationId,
                    "APPLICATION_NOTE_ADDED",
                    getCurrentUsername(),
                    ex.getMessage(),
                    buildMetadata("Note add failed"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_NOTE_ADDED failure", e);
        }
    }

    // ─── APPLICATION_NOTE_UPDATED ─────────────────────────────────────────────

    @AfterReturning(pointcut = "updateNotePointcut()", returning = "result")
    public void logNoteUpdated(JoinPoint joinPoint, Object result) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logAction(
                    "APPLICATION_NOTE", applicationId,
                    "APPLICATION_NOTE_UPDATED",
                    getCurrentUsername(),
                    null, null,
                    buildMetadata("HR updated a note on application"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_NOTE_UPDATED", e);
        }
    }

    @AfterThrowing(pointcut = "updateNotePointcut()", throwing = "ex")
    public void logNoteUpdateFailed(JoinPoint joinPoint, Throwable ex) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logFailure(
                    "APPLICATION_NOTE", applicationId,
                    "APPLICATION_NOTE_UPDATED",
                    getCurrentUsername(),
                    ex.getMessage(),
                    buildMetadata("Note update failed"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_NOTE_UPDATED failure", e);
        }
    }

    // ─── APPLICATION_NOTE_DELETED ─────────────────────────────────────────────

    @AfterReturning(pointcut = "deleteNotePointcut()")
    public void logNoteDeleted(JoinPoint joinPoint) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logAction(
                    "APPLICATION_NOTE", applicationId,
                    "APPLICATION_NOTE_DELETED",
                    getCurrentUsername(),
                    null, null,
                    buildMetadata("HR deleted a note from application"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_NOTE_DELETED", e);
        }
    }

    @AfterThrowing(pointcut = "deleteNotePointcut()", throwing = "ex")
    public void logNoteDeleteFailed(JoinPoint joinPoint, Throwable ex) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logFailure(
                    "APPLICATION_NOTE", applicationId,
                    "APPLICATION_NOTE_DELETED",
                    getCurrentUsername(),
                    ex.getMessage(),
                    buildMetadata("Note delete failed"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_NOTE_DELETED failure", e);
        }
    }

    // ─── APPLICATION_ASSIGNED ─────────────────────────────────────────────────

    @AfterReturning(pointcut = "assignApplicationPointcut()", returning = "result")
    public void logApplicationAssigned(JoinPoint joinPoint, Object result) {
        try {
            Object[] args = joinPoint.getArgs();
            String applicationId = (String) args[0];
            String assignedTo = args.length >= 2 ? (String) args[1] : null;
            Map<String, Object> afterState = new HashMap<>();
            afterState.put("assignedTo", assignedTo);
            afterState.put("assignedAt", System.currentTimeMillis());
            auditLogService.logAction(
                    "APPLICATION", applicationId,
                    "APPLICATION_ASSIGNED",
                    getCurrentUsername(),
                    null, afterState,
                    buildMetadata("Application assigned to HR: " + assignedTo));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_ASSIGNED", e);
        }
    }

    @AfterThrowing(pointcut = "assignApplicationPointcut()", throwing = "ex")
    public void logAssignFailed(JoinPoint joinPoint, Throwable ex) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logFailure(
                    "APPLICATION", applicationId,
                    "APPLICATION_ASSIGNED",
                    getCurrentUsername(),
                    ex.getMessage(),
                    buildMetadata("Application assignment failed"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_ASSIGNED failure", e);
        }
    }

    // ─── APPLICATION_UNASSIGNED ───────────────────────────────────────────────

    @AfterReturning(pointcut = "unassignApplicationPointcut()", returning = "result")
    public void logApplicationUnassigned(JoinPoint joinPoint, Object result) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logAction(
                    "APPLICATION", applicationId,
                    "APPLICATION_UNASSIGNED",
                    getCurrentUsername(),
                    null, Map.of("unassignedAt", System.currentTimeMillis()),
                    buildMetadata("HR assignment removed from application"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_UNASSIGNED", e);
        }
    }

    @AfterThrowing(pointcut = "unassignApplicationPointcut()", throwing = "ex")
    public void logUnassignFailed(JoinPoint joinPoint, Throwable ex) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logFailure(
                    "APPLICATION", applicationId,
                    "APPLICATION_UNASSIGNED",
                    getCurrentUsername(),
                    ex.getMessage(),
                    buildMetadata("Application unassignment failed"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_UNASSIGNED failure", e);
        }
    }

    // ─── APPLICATION_BULK_STATUS_CHANGED ─────────────────────────────────────

    @AfterReturning(pointcut = "bulkUpdateStatusPointcut()", returning = "result")
    public void logBulkStatusChanged(JoinPoint joinPoint, Object result) {
        try {
            Map<String, Object> afterState = new HashMap<>();
            if (result instanceof BulkUpdateResult bulkResult) {
                afterState.put("successCount", bulkResult.getSuccessCount());
                afterState.put("failureCount", bulkResult.getFailureCount());
            }
            auditLogService.logAction(
                    "APPLICATION", "bulk",
                    "APPLICATION_BULK_STATUS_CHANGED",
                    getCurrentUsername(),
                    null, afterState,
                    buildMetadata("Bulk status update performed"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_BULK_STATUS_CHANGED", e);
        }
    }

    @AfterThrowing(pointcut = "bulkUpdateStatusPointcut()", throwing = "ex")
    public void logBulkStatusChangeFailed(JoinPoint joinPoint, Throwable ex) {
        try {
            auditLogService.logFailure(
                    "APPLICATION", "bulk",
                    "APPLICATION_BULK_STATUS_CHANGED",
                    getCurrentUsername(),
                    ex.getMessage(),
                    buildMetadata("Bulk status update failed"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_BULK_STATUS_CHANGED failure", e);
        }
    }

    // ─── APPLICATION_EXPORTED ─────────────────────────────────────────────────

    @AfterReturning(pointcut = "exportApplicationsPointcut() || exportAllApplicationsPointcut()", returning = "result")
    public void logApplicationsExported(JoinPoint joinPoint, Object result) {
        try {
            boolean isAdmin = joinPoint.getSignature().getName().contains("All");
            auditLogService.logAction(
                    "APPLICATION", "export",
                    "APPLICATION_EXPORTED",
                    getCurrentUsername(),
                    null, null,
                    buildMetadata(isAdmin ? "Admin full platform export" : "Company applications export"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_EXPORTED", e);
        }
    }

    @AfterThrowing(pointcut = "exportApplicationsPointcut() || exportAllApplicationsPointcut()", throwing = "ex")
    public void logExportFailed(JoinPoint joinPoint, Throwable ex) {
        try {
            auditLogService.logFailure(
                    "APPLICATION", "export",
                    "APPLICATION_EXPORTED",
                    getCurrentUsername(),
                    ex.getMessage(),
                    buildMetadata("Applications export failed"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_EXPORTED failure", e);
        }
    }

    // ─── APPLICATION_ADMIN_CREATED ────────────────────────────────────────────

    @AfterReturning(pointcut = "adminCreatePointcut()", returning = "result")
    public void logAdminCreated(JoinPoint joinPoint, Object result) {
        try {
            if (result instanceof ApplicationResponse response) {
                Map<String, Object> afterState = Map.of(
                        "applicationId", response.getId(),
                        "jobId", response.getJobId(),
                        "username", response.getUsername());
                auditLogService.logAction(
                        "APPLICATION", response.getId(),
                        "APPLICATION_ADMIN_CREATED",
                        getCurrentUsername(),
                        null, afterState,
                        buildMetadata("Admin manually created application for user " + response.getUsername()));
            }
        } catch (Exception e) {
            log.error("Error logging APPLICATION_ADMIN_CREATED", e);
        }
    }

    @AfterThrowing(pointcut = "adminCreatePointcut()", throwing = "ex")
    public void logAdminCreateFailed(JoinPoint joinPoint, Throwable ex) {
        try {
            auditLogService.logFailure(
                    "APPLICATION", "unknown",
                    "APPLICATION_ADMIN_CREATED",
                    getCurrentUsername(),
                    ex.getMessage(),
                    buildMetadata("Admin application creation failed"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_ADMIN_CREATED failure", e);
        }
    }

    // ─── APPLICATION_ADMIN_OVERRIDE ───────────────────────────────────────────

    @AfterReturning(pointcut = "adminOverridePointcut()", returning = "result")
    public void logAdminOverride(JoinPoint joinPoint, Object result) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logAction(
                    "APPLICATION", applicationId,
                    "APPLICATION_ADMIN_OVERRIDE",
                    getCurrentUsername(),
                    null, null,
                    buildMetadata("Admin overrode application fields"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_ADMIN_OVERRIDE", e);
        }
    }

    @AfterThrowing(pointcut = "adminOverridePointcut()", throwing = "ex")
    public void logAdminOverrideFailed(JoinPoint joinPoint, Throwable ex) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logFailure(
                    "APPLICATION", applicationId,
                    "APPLICATION_ADMIN_OVERRIDE",
                    getCurrentUsername(),
                    ex.getMessage(),
                    buildMetadata("Admin override failed"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_ADMIN_OVERRIDE failure", e);
        }
    }

    // ─── APPLICATION_ADMIN_RESTORED ───────────────────────────────────────────

    @AfterReturning(pointcut = "adminRestorePointcut()", returning = "result")
    public void logAdminRestored(JoinPoint joinPoint, Object result) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logAction(
                    "APPLICATION", applicationId,
                    "APPLICATION_ADMIN_RESTORED",
                    getCurrentUsername(),
                    Map.of("deleted", true), Map.of("deleted", false),
                    buildMetadata("Admin restored soft-deleted application"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_ADMIN_RESTORED", e);
        }
    }

    @AfterThrowing(pointcut = "adminRestorePointcut()", throwing = "ex")
    public void logAdminRestoreFailed(JoinPoint joinPoint, Throwable ex) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logFailure(
                    "APPLICATION", applicationId,
                    "APPLICATION_ADMIN_RESTORED",
                    getCurrentUsername(),
                    ex.getMessage(),
                    buildMetadata("Admin restore failed"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_ADMIN_RESTORED failure", e);
        }
    }

    // ─── APPLICATION_ADMIN_DELETED ────────────────────────────────────────────

    @AfterReturning(pointcut = "adminDeletePointcut()")
    public void logAdminDeleted(JoinPoint joinPoint) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logAction(
                    "APPLICATION", applicationId,
                    "APPLICATION_ADMIN_DELETED",
                    getCurrentUsername(),
                    null, Map.of("deleted", true),
                    buildMetadata("Admin permanently deleted application"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_ADMIN_DELETED", e);
        }
    }

    @AfterThrowing(pointcut = "adminDeletePointcut()", throwing = "ex")
    public void logAdminDeleteFailed(JoinPoint joinPoint, Throwable ex) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logFailure(
                    "APPLICATION", applicationId,
                    "APPLICATION_ADMIN_DELETED",
                    getCurrentUsername(),
                    ex.getMessage(),
                    buildMetadata("Admin delete failed"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_ADMIN_DELETED failure", e);
        }
    }

    // ─── APPLICATION_ADMIN_SOFT_DELETED ──────────────────────────────────────

    @AfterReturning(pointcut = "adminSoftDeletePointcut()", returning = "result")
    public void logAdminSoftDeleted(JoinPoint joinPoint, Object result) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logAction(
                    "APPLICATION", applicationId,
                    "APPLICATION_ADMIN_SOFT_DELETED",
                    getCurrentUsername(),
                    null, Map.of("deleted", true, "deletedAt", System.currentTimeMillis()),
                    buildMetadata("Admin soft-deleted application"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_ADMIN_SOFT_DELETED", e);
        }
    }

    @AfterThrowing(pointcut = "adminSoftDeletePointcut()", throwing = "ex")
    public void logAdminSoftDeleteFailed(JoinPoint joinPoint, Throwable ex) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logFailure(
                    "APPLICATION", applicationId,
                    "APPLICATION_ADMIN_SOFT_DELETED",
                    getCurrentUsername(),
                    ex.getMessage(),
                    buildMetadata("Admin soft-delete failed"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_ADMIN_SOFT_DELETED failure", e);
        }
    }

    // ─── APPLICATION_CV_DOWNLOADED ────────────────────────────────────────────

    @AfterReturning(pointcut = "downloadCvPointcut()")
    public void logCvDownloaded(JoinPoint joinPoint) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logAction(
                    "APPLICATION", applicationId,
                    "APPLICATION_CV_DOWNLOADED",
                    getCurrentUsername(),
                    null, Map.of("downloadedAt", System.currentTimeMillis()),
                    buildMetadata("CV file downloaded for application"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_CV_DOWNLOADED", e);
        }
    }

    @AfterThrowing(pointcut = "downloadCvPointcut()", throwing = "ex")
    public void logCvDownloadFailed(JoinPoint joinPoint, Throwable ex) {
        try {
            String applicationId = (String) joinPoint.getArgs()[0];
            auditLogService.logFailure(
                    "APPLICATION", applicationId,
                    "APPLICATION_CV_DOWNLOADED",
                    getCurrentUsername(),
                    ex.getMessage(),
                    buildMetadata("CV download failed"));
        } catch (Exception e) {
            log.error("Error logging APPLICATION_CV_DOWNLOADED failure", e);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "SYSTEM";
    }

    private Map<String, Object> buildMetadata(String description) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("description", description);
        metadata.put("timestamp", System.currentTimeMillis());
        return metadata;
    }

    private String extractJobIdFromArgs(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof CreateApplicationRequest req) {
                return req.getJobId();
            }
        }
        return null;
    }
}
