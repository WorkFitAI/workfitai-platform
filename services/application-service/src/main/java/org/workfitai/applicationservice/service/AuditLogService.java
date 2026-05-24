package org.workfitai.applicationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
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
 *
 * The logAction() signature is preserved so AuditAspect needs no changes.
 * @Async removed — publisher is already fire-and-forget, no need for a thread switch.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditEventPublisher auditEventPublisher;

    // Local action name → canonical taxonomy
    private static final Map<String, String> ACTION_MAP = Map.ofEntries(
            Map.entry("CREATED",         "APP_CREATED"),
            Map.entry("ADMIN_CREATED",   "APP_CREATED"),
            Map.entry("STATUS_UPDATED",  "APP_STATUS_CHANGED"),
            Map.entry("WITHDRAWN",       "APP_WITHDRAWN"),
            Map.entry("ASSIGNED",        "APP_ASSIGNED"),
            Map.entry("ADMIN_OVERRIDE",  "APP_OVERRIDE"),
            Map.entry("ADMIN_RESTORED",  "APP_OVERRIDE"),
            Map.entry("ADMIN_DELETED",   "APP_OVERRIDE"),
            Map.entry("ADMIN_OPERATION", "APP_OVERRIDE"),
            Map.entry("NOTE_ADDED",      "APP_STATUS_CHANGED"),
            Map.entry("NOTE_UPDATED",    "APP_STATUS_CHANGED"),
            Map.entry("NOTE_DELETED",    "APP_STATUS_CHANGED"),
            Map.entry("NOTE_OPERATION",  "APP_STATUS_CHANGED")
    );

    /**
     * Log an action by publishing to Kafka.
     * Signature unchanged — callers (AuditAspect, AdminApplicationService) need no edits.
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
                    mapAction(action),
                    beforeState,
                    afterState,
                    Instant.now()
            ));
        } catch (Exception e) {
            // Never fail main operation due to audit issues
            log.error("Failed to publish audit event: {} - {} by {}", entityType, action, performedBy, e);
        }
    }

    // ─── private ─────────────────────────────────────────────────────────────

    private String mapAction(String local) {
        if (local == null) return "APP_STATUS_CHANGED";
        return ACTION_MAP.getOrDefault(local, "APP_STATUS_CHANGED");
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
}
