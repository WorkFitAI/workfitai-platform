package org.workfitai.applicationservice.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.workfitai.applicationservice.dto.kafka.AuditEvent;
import org.workfitai.applicationservice.messaging.AuditEventPublisher;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes application audit events to the Kafka "audit-events" topic.
 *
 * Replaces the old MongoDB audit_logs save. Audit data is now consumed by
 * monitoring-service and indexed in Elasticsearch (workfitai-audit-*).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditEventPublisher auditEventPublisher;

    /**
     * Log a successful action.
     */
    public void logAction(
            String entityType,
            String entityId,
            String action,
            String performedBy,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            Map<String, Object> metadata
    ) {
        publish(entityType, entityId, action, performedBy, beforeState, afterState, metadata, true, null);
    }

    /**
     * Log a failed action with an error message.
     */
    public void logFailure(
            String entityType,
            String entityId,
            String action,
            String performedBy,
            String errorMessage,
            Map<String, Object> metadata
    ) {
        publish(entityType, entityId, action, performedBy, null, null, metadata, false, errorMessage);
    }

    // ─── private ─────────────────────────────────────────────────────────────

    private void publish(
            String entityType,
            String entityId,
            String action,
            String performedBy,
            Map<String, Object> beforeState,
            Map<String, Object> afterState,
            Map<String, Object> metadata,
            Boolean success,
            String errorMessage
    ) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            auditEventPublisher.publish(new AuditEvent(
                    UUID.randomUUID().toString(),
                    "application-service",
                    performedBy,
                    extractRole(auth),
                    extractCompanyId(auth),
                    entityType,
                    entityId,
                    action,
                    beforeState,
                    afterState,
                    Instant.now(),
                    success,
                    errorMessage,
                    extractClientIp()
            ));
        } catch (Exception e) {
            log.error("Failed to publish audit event: {} - {} by {}", entityType, action, performedBy, e);
        }
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
