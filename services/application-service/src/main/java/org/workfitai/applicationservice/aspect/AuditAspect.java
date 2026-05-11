package org.workfitai.applicationservice.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.model.enums.ApplicationStatus;
import org.workfitai.applicationservice.repository.ApplicationRepository;
import org.workfitai.applicationservice.service.AuditLogService;

import java.util.HashMap;
import java.util.Map;

/**
 * AOP Aspect for automatic audit logging.
 * Intercepts critical service methods and writes audit trail entries.
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

    @Pointcut("execution(* org.workfitai.applicationservice.saga.ApplicationSagaOrchestrator.createApplication(..))")
    public void createApplicationPointcut() {
    }

    @Pointcut("execution(* org.workfitai.applicationservice.service.impl.ApplicationServiceImpl.updateStatus(..))")
    public void updateStatusPointcut() {
    }

    @Pointcut("execution(* org.workfitai.applicationservice.service.impl.ApplicationServiceImpl.withdrawApplication(..))")
    public void withdrawApplicationPointcut() {
    }

    @Pointcut("execution(* org.workfitai.applicationservice.service.ApplicationNoteService.*(..))")
    public void noteOperationsPointcut() {
    }

    @Pointcut("execution(* org.workfitai.applicationservice.service.AssignmentService.assignApplication(..))")
    public void assignmentPointcut() {
    }

    @Pointcut("execution(* org.workfitai.applicationservice.service.AdminApplicationService.*(..))")
    public void adminOperationsPointcut() {
    }

    // Capture real previous status from DB before the update runs (H1 fix)
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

    @AfterReturning(pointcut = "createApplicationPointcut()", returning = "result")
    public void logApplicationCreation(JoinPoint joinPoint, Object result) {
        try {
            if (result instanceof ApplicationResponse response) {
                Map<String, Object> afterState = new HashMap<>();
                afterState.put("applicationId", response.getId());
                afterState.put("jobId", response.getJobId());
                afterState.put("status", response.getStatus());
                afterState.put("username", response.getUsername());

                auditLogService.logAction(
                        "APPLICATION",
                        response.getId(),
                        "CREATED",
                        getCurrentUsername(),
                        null,
                        afterState,
                        buildMetadata("Application created"));
            }
        } catch (Exception e) {
            log.error("Error logging application creation", e);
        }
    }

    @AfterReturning(pointcut = "updateStatusPointcut()", returning = "result")
    public void logStatusUpdate(JoinPoint joinPoint, Object result) {
        try {
            Object[] args = joinPoint.getArgs();
            String applicationId = (String) args[0];

            // Use the status captured in @Before (real previous status, not the new one)
            ApplicationStatus previousStatus = previousStatusHolder.get();

            if (result instanceof ApplicationResponse response) {
                Map<String, Object> beforeState = previousStatus != null
                        ? Map.of("status", previousStatus)
                        : null;

                Map<String, Object> afterState = new HashMap<>();
                afterState.put("status", response.getStatus());
                afterState.put("updatedAt", response.getUpdatedAt());

                auditLogService.logAction(
                        "APPLICATION",
                        applicationId,
                        "STATUS_UPDATED",
                        getCurrentUsername(),
                        beforeState,
                        afterState,
                        buildMetadata("Status updated"));
            }
        } catch (Exception e) {
            log.error("Error logging status update", e);
        } finally {
            previousStatusHolder.remove();
        }
    }

    @AfterReturning(pointcut = "withdrawApplicationPointcut()", returning = "result")
    public void logWithdrawal(JoinPoint joinPoint, Object result) {
        try {
            Object[] args = joinPoint.getArgs();
            String applicationId = (String) args[0];

            Map<String, Object> afterState = new HashMap<>();
            afterState.put("deletedAt", System.currentTimeMillis());
            afterState.put("status", "WITHDRAWN");

            auditLogService.logAction(
                    "APPLICATION",
                    applicationId,
                    "WITHDRAWN",
                    getCurrentUsername(),
                    null,
                    afterState,
                    buildMetadata("Application withdrawn by candidate"));
        } catch (Exception e) {
            log.error("Error logging withdrawal", e);
        }
    }

    @AfterReturning(pointcut = "noteOperationsPointcut()", returning = "result")
    public void logNoteOperation(JoinPoint joinPoint, Object result) {
        try {
            String methodName = joinPoint.getSignature().getName();
            Object[] args = joinPoint.getArgs();

            String action = switch (methodName) {
                case "addNote" -> "NOTE_ADDED";
                case "updateNote" -> "NOTE_UPDATED";
                case "deleteNote" -> "NOTE_DELETED";
                default -> "NOTE_OPERATION";
            };

            if (args.length > 0 && args[0] instanceof String applicationId) {
                auditLogService.logAction(
                        "APPLICATION_NOTE",
                        applicationId,
                        action,
                        getCurrentUsername(),
                        null,
                        null,
                        buildMetadata("Note " + methodName));
            }
        } catch (Exception e) {
            log.error("Error logging note operation", e);
        }
    }

    @AfterReturning(pointcut = "assignmentPointcut()", returning = "result")
    public void logAssignment(JoinPoint joinPoint, Object result) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args.length >= 2) {
                String applicationId = (String) args[0];
                String assignedTo = (String) args[1];

                Map<String, Object> afterState = new HashMap<>();
                afterState.put("assignedTo", assignedTo);
                afterState.put("assignedAt", System.currentTimeMillis());

                auditLogService.logAction(
                        "APPLICATION",
                        applicationId,
                        "ASSIGNED",
                        getCurrentUsername(),
                        null,
                        afterState,
                        buildMetadata("Application assigned to " + assignedTo));
            }
        } catch (Exception e) {
            log.error("Error logging assignment", e);
        }
    }

    @AfterReturning(pointcut = "adminOperationsPointcut()", returning = "result")
    public void logAdminOperation(JoinPoint joinPoint, Object result) {
        try {
            String methodName = joinPoint.getSignature().getName();

            String action = switch (methodName) {
                case "createApplication" -> "ADMIN_CREATED";
                case "overrideApplication" -> "ADMIN_OVERRIDE";
                case "restoreApplication" -> "ADMIN_RESTORED";
                case "deleteApplication" -> "ADMIN_DELETED";
                default -> "ADMIN_OPERATION";
            };

            Object[] args = joinPoint.getArgs();
            String applicationId = null;

            if (result instanceof ApplicationResponse response) {
                applicationId = response.getId();
            } else if (args.length > 0 && args[0] instanceof String s) {
                applicationId = s;
            }

            if (applicationId != null) {
                Map<String, Object> metadata = buildMetadata("Admin operation: " + methodName);
                metadata.put("adminAction", true);
                metadata.put("methodName", methodName);

                auditLogService.logAction(
                        "APPLICATION",
                        applicationId,
                        action,
                        getCurrentUsername(),
                        null,
                        null,
                        metadata);
            }
        } catch (Exception e) {
            log.error("Error logging admin operation", e);
        }
    }

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
}
