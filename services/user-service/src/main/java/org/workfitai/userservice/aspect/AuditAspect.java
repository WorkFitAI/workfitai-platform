package org.workfitai.userservice.aspect;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.workfitai.userservice.dto.kafka.AuditEvent;
import org.workfitai.userservice.dto.response.HRResponse;
import org.workfitai.userservice.messaging.AuditEventPublisher;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes user lifecycle audit events to Kafka "audit-events".
 *
 * Intercepts UserServiceImpl (block/unblock/delete) and HRServiceImpl
 * (approve/reject HR and HR_MANAGER) after successful completion.
 * Both UUID-based and username-based controller endpoints are covered.
 *
 * Fire-and-forget — audit failure is logged but never propagates to caller.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditEventPublisher publisher;

    // ─── Block / Unblock ──────────────────────────────────────────────────────

    /** Fires when AdminController calls setUserBlockStatus(UUID, boolean) directly. */
    @AfterReturning("execution(* org.workfitai.userservice.service.impl.UserServiceImpl.setUserBlockStatus(..))")
    public void logBlockByUuid(JoinPoint jp) {
        UUID userId = (UUID) jp.getArgs()[0];
        boolean blocked = (boolean) jp.getArgs()[1];
        publish("USER", userId.toString(), blocked ? "USER_BLOCKED" : "USER_UNBLOCKED", null, null);
    }

    /** Fires when AdminController calls setUserBlockStatusByUsername(String, boolean, String). */
    @AfterReturning("execution(* org.workfitai.userservice.service.impl.UserServiceImpl.setUserBlockStatusByUsername(..))")
    public void logBlockByUsername(JoinPoint jp) {
        String username = (String) jp.getArgs()[0];
        boolean blocked = (boolean) jp.getArgs()[1];
        publish("USER", username, blocked ? "USER_BLOCKED" : "USER_UNBLOCKED", null, null);
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    /** Fires when AdminController calls deleteUser(UUID) directly. */
    @AfterReturning("execution(* org.workfitai.userservice.service.impl.UserServiceImpl.deleteUser(..))")
    public void logDeleteByUuid(JoinPoint jp) {
        UUID userId = (UUID) jp.getArgs()[0];
        publish("USER", userId.toString(), "USER_DELETED", null, null);
    }

    /** Fires when AdminController calls deleteUserByUsername(String, String). */
    @AfterReturning("execution(* org.workfitai.userservice.service.impl.UserServiceImpl.deleteUserByUsername(..))")
    public void logDeleteByUsername(JoinPoint jp) {
        String username = (String) jp.getArgs()[0];
        publish("USER", username, "USER_DELETED", null, null);
    }

    // ─── Approve HR / HR_MANAGER (UUID endpoints) ────────────────────────────

    @AfterReturning(
        pointcut = "execution(* org.workfitai.userservice.service.impl.HRServiceImpl.approveHrManager(..))" +
                   " || execution(* org.workfitai.userservice.service.impl.HRServiceImpl.approveHr(..))",
        returning = "result"
    )
    public void logApproveByUuid(Object result) {
        logApprove(result);
    }

    // ─── Approve HR / HR_MANAGER (username endpoints) ────────────────────────

    @AfterReturning(
        pointcut = "execution(* org.workfitai.userservice.service.impl.HRServiceImpl.approveHrManagerByUsername(..))" +
                   " || execution(* org.workfitai.userservice.service.impl.HRServiceImpl.approveHrByUsername(..))",
        returning = "result"
    )
    public void logApproveByUsername(Object result) {
        logApprove(result);
    }

    // ─── Reject HR / HR_MANAGER (UUID endpoints) ─────────────────────────────

    @AfterReturning(
        pointcut = "execution(* org.workfitai.userservice.service.impl.HRServiceImpl.rejectHrManager(..))" +
                   " || execution(* org.workfitai.userservice.service.impl.HRServiceImpl.rejectHr(..))",
        returning = "result"
    )
    public void logRejectByUuid(Object result) {
        logReject(result);
    }

    // ─── Reject HR / HR_MANAGER (username endpoints) ─────────────────────────

    @AfterReturning(
        pointcut = "execution(* org.workfitai.userservice.service.impl.HRServiceImpl.rejectHrManagerByUsername(..))" +
                   " || execution(* org.workfitai.userservice.service.impl.HRServiceImpl.rejectHrByUsername(..))",
        returning = "result"
    )
    public void logRejectByUsername(Object result) {
        logReject(result);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void logApprove(Object result) {
        if (!(result instanceof HRResponse hr)) return;
        publish("USER", hr.getUsername(), "USER_APPROVED", null, buildAfterState(hr));
    }

    private void logReject(Object result) {
        if (!(result instanceof HRResponse hr)) return;
        publish("USER", hr.getUsername(), "USER_REJECTED", null, buildAfterState(hr));
    }

    private Map<String, Object> buildAfterState(HRResponse hr) {
        Map<String, Object> after = new HashMap<>();
        if (hr.getUserRole() != null) after.put("role", hr.getUserRole().name());
        if (hr.getUserStatus() != null) after.put("status", hr.getUserStatus().name());
        if (hr.getCompanyId() != null) after.put("companyId", hr.getCompanyId().toString());
        return after;
    }

    private void publish(String entityType, String entityId, String action,
                         Map<String, Object> before, Map<String, Object> after) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            publisher.publish(new AuditEvent(
                    UUID.randomUUID().toString(),
                    "user-service",
                    extractUsername(auth),
                    extractRole(auth),
                    extractCompanyId(auth),
                    entityType,
                    entityId,
                    action,
                    before,
                    after,
                    Instant.now(),
                    true,
                    null,
                    extractClientIp()
            ));
        } catch (Exception e) {
            log.error("[AUDIT] Failed to publish audit event [{} {}]: {}", action, entityId, e.getMessage());
        }
    }

    private String extractUsername(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }
        return auth != null ? auth.getName() : "SYSTEM";
    }

    private String extractRole(Authentication auth) {
        if (auth == null) return "SYSTEM";
        // Prefer ROLE_* prefixed authorities (Spring standard)
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5)) // strip "ROLE_" prefix
                .findFirst()
                .orElseGet(() -> auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(a -> !a.contains(":"))
                        .findFirst()
                        .orElse("SYSTEM"));
    }

    private String extractCompanyId(Authentication auth) {
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Object claim = jwtAuth.getToken().getClaims().get("companyId");
            return claim != null ? claim.toString() : null;
        }
        return null;
    }

    private String extractClientIp() {
        try {
            HttpServletRequest req = ((ServletRequestAttributes)
                    RequestContextHolder.currentRequestAttributes()).getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
