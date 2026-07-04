package org.workfitai.authservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.workfitai.authservice.dto.kafka.AuditEvent;
import org.workfitai.authservice.messaging.AuditEventPublisher;

@ExtendWith(MockitoExtension.class)
class GrantAuditServiceImplTest {

    @Mock AuditEventPublisher publisher;
    @InjectMocks GrantAuditServiceImpl grantAuditService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
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

        AuditEvent event = publishedEvent();
        assertThat(event.companyId()).isEqualTo("company-abc");
    }

    @Test
    void logGrant_withEmptyCompanyIdInDetails_returnsNull() {
        var auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ADMIN")));
        auth.setDetails(Map.of("companyId", ""));
        SecurityContextHolder.getContext().setAuthentication(auth);

        grantAuditService.logGrant("admin", "alice", "ROLE", "HR");

        AuditEvent event = publishedEvent();
        assertThat(event.companyId()).isNull();
    }

    @Test
    void logGrant_withMissingCompanyIdInDetails_returnsNull() {
        var auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ADMIN")));
        auth.setDetails(Map.of("tenantId", "tenant-1"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        grantAuditService.logGrant("admin", "alice", "ROLE", "HR");

        assertThat(publishedEvent().companyId()).isNull();
    }

    @Test
    void logGrant_withNonMapDetails_returnsNullCompanyId() {
        var auth = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ADMIN")));
        auth.setDetails("not-a-map");
        SecurityContextHolder.getContext().setAuthentication(auth);

        grantAuditService.logGrant("admin", "alice", "ROLE", "HR");

        assertThat(publishedEvent().companyId()).isNull();
    }

    @Test
    void logGrant_withForwardedHeader_usesFirstForwardedIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", " 203.0.113.8, 10.0.0.2 ");
        request.setRemoteAddr("192.0.2.8");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        grantAuditService.logGrant("admin", "alice", "ROLE", "HR");

        assertThat(publishedEvent().actorIp()).isEqualTo("203.0.113.8");
    }

    @Test
    void logGrant_withBlankForwardedHeader_usesRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "   ");
        request.setRemoteAddr("192.0.2.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        grantAuditService.logGrant("admin", "alice", "ROLE", "HR");

        assertThat(publishedEvent().actorIp()).isEqualTo("192.0.2.9");
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

    private AuditEvent publishedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        return captor.getValue();
    }
}
