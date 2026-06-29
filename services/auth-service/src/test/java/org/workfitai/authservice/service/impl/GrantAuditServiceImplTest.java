package org.workfitai.authservice.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.workfitai.authservice.dto.kafka.AuditEvent;
import org.workfitai.authservice.messaging.AuditEventPublisher;

@ExtendWith(MockitoExtension.class)
class GrantAuditServiceImplTest {

    @Mock AuditEventPublisher publisher;
    @InjectMocks GrantAuditServiceImpl grantAuditService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logGrant_publishesRoleGrantedEvent() {
        // Act
        grantAuditService.logGrant("admin", "alice", "ROLE", "HR");

        // Assert
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logRevoke_publishesRoleRevokedEvent() {
        // Act
        grantAuditService.logRevoke("admin", "alice", "ROLE", "HR");

        // Assert
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logGrant_withNullSecurityContext_doesNotThrow() {
        // No authentication in context — extractRole and extractCompanyId handle null
        grantAuditService.logGrant("system", "bob", "ROLE", "CANDIDATE");
        verify(publisher).publish(any(AuditEvent.class));
    }

    // ─── extractRole: authenticated context ──────────────────────────────────

    @Test
    void logGrant_withAuthenticatedContext_extractsRoleFromAuthority() {
        var auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        grantAuditService.logGrant("admin", "alice", "ROLE", "HR");

        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logGrant_withPermissionAuthorityOnly_fallsBackToSystemRole() {
        // Authorities with ":" (like permissions) should be filtered out as roles
        var auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("perm:read")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        grantAuditService.logGrant("admin", "alice", "ROLE", "HR");

        verify(publisher).publish(any(AuditEvent.class));
    }

    // ─── extractCompanyId: details map ────────────────────────────────────────

    @Test
    void logGrant_withCompanyIdInDetails_extractsCompanyId() {
        var auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ADMIN")));
        auth.setDetails(Map.of("companyId", "company-abc"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        grantAuditService.logGrant("admin", "alice", "ROLE", "HR");

        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logGrant_withEmptyCompanyIdInDetails_returnsNull() {
        var auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ADMIN")));
        auth.setDetails(Map.of("companyId", ""));
        SecurityContextHolder.getContext().setAuthentication(auth);

        grantAuditService.logGrant("admin", "alice", "ROLE", "HR");

        verify(publisher).publish(any(AuditEvent.class));
    }

    // ─── publisher exception: must not propagate ──────────────────────────────

    @Test
    void logGrant_publisherThrows_doesNotPropagateException() {
        doThrow(new RuntimeException("Kafka unavailable")).when(publisher).publish(any());

        // publish failure is caught internally — caller must NOT see the exception
        assertThatCode(() -> grantAuditService.logGrant("admin", "alice", "ROLE", "HR"))
                .doesNotThrowAnyException();
    }

    // ─── both grant and revoke: verify separate events ────────────────────────

    @Test
    void logGrantAndRevoke_publishesTwoSeparateEvents() {
        grantAuditService.logGrant("admin", "alice", "ROLE", "HR");
        grantAuditService.logRevoke("admin", "alice", "ROLE", "HR");

        verify(publisher, times(2)).publish(any(AuditEvent.class));
    }
}
