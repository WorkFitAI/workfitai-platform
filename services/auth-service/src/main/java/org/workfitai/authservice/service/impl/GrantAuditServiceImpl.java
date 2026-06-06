package org.workfitai.authservice.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.workfitai.authservice.dto.kafka.AuditEvent;
import org.workfitai.authservice.messaging.AuditEventPublisher;
import org.workfitai.authservice.service.iGrantAuditService;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes ROLE_GRANTED / ROLE_REVOKED audit events to the Kafka "audit-events" topic.
 *
 * Intentionally NOT @Transactional — audit failure must never roll back a grant/revoke.
 * MongoDB grant_audit_logs is no longer used; Elasticsearch (via monitoring-service) is
 * the durable audit store.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrantAuditServiceImpl implements iGrantAuditService {

    private final AuditEventPublisher publisher;

    @Override
    public void logGrant(String grantorUsername, String targetUsername,
                         String resourceType, String resourceName) {
        publish(grantorUsername, targetUsername, "ROLE_GRANTED", resourceType, resourceName);
    }

    @Override
    public void logRevoke(String grantorUsername, String targetUsername,
                          String resourceType, String resourceName) {
        publish(grantorUsername, targetUsername, "ROLE_REVOKED", resourceType, resourceName);
    }

    // ─── private ─────────────────────────────────────────────────────────────────

    private void publish(String grantor, String target, String action,
                         String resourceType, String resourceName) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String actorRole = extractRole(auth);
            String companyId = extractCompanyId(auth);

            AuditEvent event = new AuditEvent(
                    UUID.randomUUID().toString(),
                    "auth-service",
                    grantor,
                    actorRole,
                    companyId,
                    "ROLE",
                    resourceName,
                    action,
                    null,
                    Map.of("targetUsername", target, "roleName", resourceName, "resourceType", resourceType),
                    Instant.now(),
                    true,
                    null,
                    extractClientIp()
            );

            publisher.publish(event);

        } catch (Exception ex) {
            log.error("[AUDIT] Failed to publish audit event [{} {} -> {}]: {}",
                    action, resourceName, target, ex.getMessage());
        }
    }

    private String extractRole(Authentication auth) {
        if (auth == null) return "SYSTEM";
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> !a.contains(":"))
                .findFirst()
                .orElse("SYSTEM");
    }

    /** Reads companyId from details stored by JwtAuthenticationFilter. */
    private String extractCompanyId(Authentication auth) {
        if (auth != null && auth.getDetails() instanceof Map<?, ?> details) {
            Object claim = details.get("companyId");
            return (claim != null && !claim.toString().isEmpty()) ? claim.toString() : null;
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
