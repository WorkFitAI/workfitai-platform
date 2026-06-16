package org.workfitai.cvservice.aspect;

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
import org.workfitai.cvservice.dto.kafka.AuditEvent;
import org.workfitai.cvservice.messaging.AuditEventPublisher;
import org.workfitai.cvservice.model.dto.response.ResCvDTO;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes CV lifecycle audit events to Kafka "audit-events".
 *
 * Intercepts CVService (create, update, delete, download) after successful completion.
 * Fire-and-forget — audit failure is logged but never propagates to caller.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditEventPublisher publisher;

    // ─── CV Create ────────────────────────────────────────────────────────────

    @AfterReturning(
        pointcut = "execution(* org.workfitai.cvservice.service.impl.CVService.createCv(..))",
        returning = "result"
    )
    public void logCvCreated(Object result) {
        if (!(result instanceof ResCvDTO cv)) return;
        publish("CV", cv.getCvId(), "CV_CREATED", null, Map.of("belongTo", orEmpty(cv.getBelongTo())));
    }

    // ─── CV Update ────────────────────────────────────────────────────────────

    @AfterReturning(
        pointcut = "execution(* org.workfitai.cvservice.service.impl.CVService.update(..))",
        returning = "result"
    )
    public void logCvUpdated(Object result) {
        if (!(result instanceof ResCvDTO cv)) return;
        publish("CV", cv.getCvId(), "CV_UPDATED", null, Map.of("belongTo", orEmpty(cv.getBelongTo())));
    }

    // ─── CV Delete ────────────────────────────────────────────────────────────

    @AfterReturning("execution(* org.workfitai.cvservice.service.impl.CVService.delete(..))")
    public void logCvDeleted(JoinPoint jp) {
        String cvId = (String) jp.getArgs()[0];
        publish("CV", cvId, "CV_DELETED", null, null);
    }

    // ─── CV Download ─────────────────────────────────────────────────────────

    @AfterReturning("execution(* org.workfitai.cvservice.service.impl.CVService.downloadCV(..))")
    public void logCvDownloaded(JoinPoint jp) {
        String objectName = (String) jp.getArgs()[0];
        publish("CV", objectName, "CV_DOWNLOADED", null, null);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void publish(String entityType, String entityId, String action,
                         Map<String, Object> before, Map<String, Object> after) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            publisher.publish(new AuditEvent(
                    UUID.randomUUID().toString(),
                    "cv-service",
                    extractUsername(auth),
                    extractRole(auth),
                    null,
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
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst()
                .orElseGet(() -> auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .filter(a -> !a.contains(":"))
                        .findFirst()
                        .orElse("SYSTEM"));
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

    private String orEmpty(String value) {
        return value != null ? value : "";
    }
}
