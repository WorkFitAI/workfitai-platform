package org.workfitai.authservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
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

            // auth-service JwtAuthenticationFilter stores roles as plain names (e.g. "ADMIN", "HR_MANAGER")
            // not ROLE_-prefixed, so we search the authorities as-is.
            String actorRole = extractRole(auth);

            // companyId: auth-service's JwtAuthenticationFilter sets UsernamePasswordAuthenticationToken
            // and does not parse the companyId claim into the principal details.
            // ADMIN granting HR_MANAGER → platform-level (null is correct).
            // HR_MANAGER granting HR → company-scoped; companyId enhancement tracked as open issue.
            String companyId = null;

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
                    Instant.now()
            );

            publisher.publish(event);

        } catch (Exception ex) {
            // Audit failure is non-fatal — log and continue
            log.error("[AUDIT] Failed to publish audit event [{} {} -> {}]: {}",
                    action, resourceName, target, ex.getMessage());
        }
    }

    /** Extracts the first non-permission authority as the actor role. */
    private String extractRole(Authentication auth) {
        if (auth == null) return "SYSTEM";
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> !a.contains(":")) // skip permission authorities (e.g. "role:grant")
                .findFirst()
                .orElse("SYSTEM");
    }
}
