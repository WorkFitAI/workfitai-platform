package org.workfitai.applicationservice.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.workfitai.applicationservice.dto.request.*;
import org.workfitai.applicationservice.dto.response.ApplicationResponse;
import org.workfitai.applicationservice.model.Application;
import org.workfitai.applicationservice.service.AuditLogService;

import java.util.HashMap;
import java.util.Map;

/**
 * AOP Aspect for automatic audit logging
 * Intercepts critical service methods and logs changes
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /**
     * Pointcut for application creation
     */
    @Pointcut("execution(* org.workfitai.applicationservice.service.impl.ApplicationServiceImpl.createApplication(..))")
    public void createApplicationPointcut() {
    }

    /**
     * Pointcut for application updates
     */
    @Pointcut("execution(* org.workfitai.applicationservice.service.impl.ApplicationServiceImpl.updateStatus(..))")
    public void updateStatusPointcut() {
    }

    /**
     * Pointcut for application withdrawal
     */
    @Pointcut("execution(* org.workfitai.applicationservice.service.impl.ApplicationServiceImpl.withdrawApplication(..))")
    public void withdrawApplicationPointcut() {
    }

    /**
     * Pointcut for note operations
     */
    @Pointcut("execution(* org.workfitai.applicationservice.service.ApplicationNoteService.*(..))")
    public void noteOperationsPointcut() {
    }

    /**
     * Pointcut for assignment operations
     */
    @Pointcut("execution(* org.workfitai.applicationservice.service.AssignmentService.assignApplication(..))")
    public void assignmentPointcut() {
    }

    /**
     * Pointcut for admin operations
     */
    @Pointcut("execution(* org.workfitai.applicationservice.service.AdminApplicationService.*(..))")
    public void adminOperationsPointcut() {
    }

    /**
     * Log application creation
     */
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

    /**
     * Log status updates
     */
    @AfterReturning(pointcut = "updateStatusPointcut()", returning = "result")
    public void logStatusUpdate(JoinPoint joinPoint, Object result) {
        try {
            Object[] args = joinPoint.getArgs();
            String applicationId = (String) args[0];

            if (result instanceof ApplicationResponse response) {
                Map<String, Object> beforeState = new HashMap<>();
                Map<String, Object> afterState = new HashMap<>();

                afterState.put("status", response.getStatus());
                afterState.put("updatedAt", response.getUpdatedAt());

                // Extract reason from request if available
                Map<String, Object> metadata = buildMetadata("Status updated");
                if (args.length > 1 && args[1] instanceof UpdateStatusRequest request) {
                    beforeState.put("status", request.getStatus());
                    if (request.getNote() != null) {
                        metadata.put("reason", request.getNote());
                    }
                }

                auditLogService.logAction(
                        "APPLICATION",
                        applicationId,
                        "STATUS_UPDATED",
                        getCurrentUsername(),
                        beforeState,
                        afterState,
                        metadata);
            }
        } catch (Exception e) {
            log.error("Error logging status update", e);
        }
    }

    /**
     * Log application withdrawal
     */
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

    /**
     * Log note operations
     */
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
                Map<String, Object> metadata = buildMetadata("Note " + methodName);

                auditLogService.logAction(
                        "APPLICATION_NOTE",
                        applicationId,
                        action,
                        getCurrentUsername(),
                        null,
                        null,
                        metadata);
            }
        } catch (Exception e) {
            log.error("Error logging note operation", e);
        }
    }

    /**
     * Log assignment operations
     */
    @AfterReturning(pointcut = "assignmentPointcut()", returning = "result")
    public void logAssignment(JoinPoint joinPoint, Object result) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args.length >= 2) {
                String applicationId = (String) args[0];
                AssignApplicationRequest request = (AssignApplicationRequest) args[1];

                Map<String, Object> afterState = new HashMap<>();
                afterState.put("assignedTo", request.getAssignedTo());
                afterState.put("assignedAt", System.currentTimeMillis());

                auditLogService.logAction(
                        "APPLICATION",
                        applicationId,
                        "ASSIGNED",
                        getCurrentUsername(),
                        null,
                        afterState,
                        buildMetadata("Application assigned to " + request.getAssignedTo()));
            }
        } catch (Exception e) {
            log.error("Error logging assignment", e);
        }
    }

    /**
     * Log admin operations (create, override, restore)
     */
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
            } else if (args.length > 0 && args[0] instanceof String) {
                applicationId = (String) args[0];
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

    /**
     * Get current username from security context
     */
    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return "SYSTEM";
    }

    /**
     * Build metadata map
     */
    private Map<String, Object> buildMetadata(String description) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("description", description);
        metadata.put("timestamp", System.currentTimeMillis());
        return metadata;
    }
}
