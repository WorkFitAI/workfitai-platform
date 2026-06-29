package org.workfitai.applicationservice.service;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.workfitai.applicationservice.dto.kafka.AuditEvent;
import org.workfitai.applicationservice.messaging.AuditEventPublisher;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock AuditEventPublisher auditEventPublisher;

    @InjectMocks AuditLogService auditLogService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ─── logAction ────────────────────────────────────────────────────────────

    @Test
    void logAction_publishesSuccessEvent() {
        auditLogService.logAction(
                "Application", "app-1", "STATUS_UPDATE",
                "hr1",
                Map.of("status", "APPLIED"),
                Map.of("status", "REVIEWING"),
                Map.of("reason", "approved"));

        verify(auditEventPublisher).publish(argThat(event ->
                event.entityType().equals("Application")
                && event.entityId().equals("app-1")
                && event.action().equals("STATUS_UPDATE")
                && event.actorUsername().equals("hr1")
                && Boolean.TRUE.equals(event.success())
                && event.errorMessage() == null));
    }

    @Test
    void logAction_withJwtAuth_extractsRoleAndCompany() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
                .claim("sub", "hr1").claim("companyId", "company-1").build();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_HR")), "hr1");
        SecurityContextHolder.getContext().setAuthentication(auth);

        auditLogService.logAction("Application", "app-1", "NOTE_ADDED", "hr1",
                null, null, null);

        verify(auditEventPublisher).publish(argThat(event ->
                "HR".equals(event.actorRole())
                && "company-1".equals(event.companyId())));
    }

    @Test
    void logAction_noSecurityContext_usesSystemRole() {
        SecurityContextHolder.clearContext();

        auditLogService.logAction("Application", "app-1", "SYSTEM_OP", "system",
                null, null, null);

        verify(auditEventPublisher).publish(argThat(event ->
                "SYSTEM".equals(event.actorRole())));
    }

    @Test
    void logAction_publisherThrows_exceptionSwallowed() {
        doThrow(new RuntimeException("Kafka down")).when(auditEventPublisher).publish(argThat(e -> true));

        // must not propagate
        auditLogService.logAction("Application", "app-1", "OP", "hr1",
                null, null, null);
    }

    // ─── logFailure ───────────────────────────────────────────────────────────

    @Test
    void logFailure_publishesFailureEvent() {
        auditLogService.logFailure(
                "Application", "app-1", "STATUS_UPDATE",
                "hr1", "Invalid transition",
                Map.of("attempted", "HIRED"));

        verify(auditEventPublisher).publish(argThat(event ->
                event.entityType().equals("Application")
                && event.entityId().equals("app-1")
                && Boolean.FALSE.equals(event.success())
                && "Invalid transition".equals(event.errorMessage())
                && event.before() == null
                && event.after() == null));
    }

    @Test
    void logFailure_publisherThrows_exceptionSwallowed() {
        doThrow(new RuntimeException("Kafka down")).when(auditEventPublisher).publish(argThat(e -> true));

        // must not propagate
        auditLogService.logFailure("Application", "app-1", "OP", "hr1", "error", null);
    }
}
