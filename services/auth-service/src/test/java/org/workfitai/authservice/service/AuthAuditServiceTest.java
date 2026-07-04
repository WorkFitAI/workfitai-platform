package org.workfitai.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
class AuthAuditServiceTest {

    @Mock AuditEventPublisher publisher;
    @InjectMocks AuthAuditService auditService;

    @AfterEach
    void clearContexts() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    // ─── Auth events ──────────────────────────────────────────────────────────

    @Test
    void logLoginSuccess_publishesAuditEvent() {
        auditService.logLoginSuccess("alice");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logLoginFailed_publishesAuditEvent() {
        auditService.logLoginFailed("alice", "Bad credentials");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logLogout_publishesAuditEvent() {
        auditService.logLogout("alice");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logRegister_publishesAuditEvent() {
        auditService.logRegister("alice");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logRegisterFailed_publishesAuditEvent() {
        auditService.logRegisterFailed("alice", "Email already taken");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logOtpVerified_publishesAuditEvent() {
        auditService.logOtpVerified("alice");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logOtpVerifyFailed_publishesAuditEvent() {
        auditService.logOtpVerifyFailed("alice@example.com", "Invalid OTP");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void log2FALoginSuccess_publishesAuditEvent() {
        auditService.log2FALoginSuccess("alice");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void log2FALoginFailed_publishesAuditEvent() {
        auditService.log2FALoginFailed("alice", "Wrong TOTP");
        verify(publisher).publish(any(AuditEvent.class));
    }

    // ─── Password events ──────────────────────────────────────────────────────

    @Test
    void logPasswordChanged_publishesAuditEvent() {
        auditService.logPasswordChanged("alice");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logForgotPasswordRequested_publishesAuditEvent() {
        auditService.logForgotPasswordRequested("alice@example.com");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logPasswordReset_publishesAuditEvent() {
        auditService.logPasswordReset("alice");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logPasswordSet_publishesAuditEvent() {
        auditService.logPasswordSet("alice");
        verify(publisher).publish(any(AuditEvent.class));
    }

    // ─── 2FA events ───────────────────────────────────────────────────────────

    @Test
    void log2FAEnabled_publishesAuditEvent() {
        auditService.log2FAEnabled("alice", "TOTP");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void log2FADisabled_publishesAuditEvent() {
        auditService.log2FADisabled("alice");
        verify(publisher).publish(any(AuditEvent.class));
    }

    // ─── OAuth events ─────────────────────────────────────────────────────────

    @Test
    void logOAuthLinked_publishesAuditEvent() {
        auditService.logOAuthLinked("alice", "google");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logOAuthUnlinked_publishesAuditEvent() {
        auditService.logOAuthUnlinked("alice", "google");
        verify(publisher).publish(any(AuditEvent.class));
    }

    // ─── Approval events ──────────────────────────────────────────────────────

    @Test
    void logHRManagerApproved_publishesAuditEvent() {
        auditService.logHRManagerApproved("admin", "user-123");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logHRApproved_publishesAuditEvent() {
        auditService.logHRApproved("admin", "user-456");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logUserRejected_publishesAuditEvent() {
        auditService.logUserRejected("admin", "user-789", "Invalid info");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logUserRejected_withNullReason_usesEmptyReason() {
        auditService.logUserRejected("admin", "user-789", null);

        AuditEvent event = publishedEvent();
        assertThat(event.after()).containsEntry("reason", "");
    }

    // ─── Session events ───────────────────────────────────────────────────────

    @Test
    void logSessionDeleted_publishesAuditEvent() {
        auditService.logSessionDeleted("alice", "session-abc");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logAllSessionsDeleted_publishesAuditEvent() {
        auditService.logAllSessionsDeleted("alice");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logTokenRefreshed_publishesAuditEvent() {
        auditService.logTokenRefreshed("alice");
        verify(publisher).publish(any(AuditEvent.class));
    }

    // ─── publish does not throw when SecurityContext is empty ─────────────────

    @Test
    void logLoginSuccess_withEmptySecurityContext_doesNotThrow() {
        // No SecurityContext set — extractCurrentUsername returns "system"
        // extractClientIp returns null (no request context)
        auditService.logLoginSuccess("alice");
        verify(publisher).publish(any(AuditEvent.class));
    }

    // ─── Password failed / reset variants ─────────────────────────────────────

    @Test
    void logPasswordChangeFailed_publishesAuditEvent() {
        auditService.logPasswordChangeFailed("alice", "Wrong old password");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logPasswordResetOtpVerified_publishesAuditEvent() {
        auditService.logPasswordResetOtpVerified("alice@example.com");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logPasswordResetOtpFailed_publishesAuditEvent() {
        auditService.logPasswordResetOtpFailed("alice@example.com", "Wrong OTP");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logPasswordResetFailed_publishesAuditEvent() {
        auditService.logPasswordResetFailed("Token expired");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logPasswordSetFailed_publishesAuditEvent() {
        auditService.logPasswordSetFailed("alice", "Password too weak");
        verify(publisher).publish(any(AuditEvent.class));
    }

    // ─── 2FA failed variants ──────────────────────────────────────────────────

    @Test
    void log2FAEnableFailed_publishesAuditEvent() {
        auditService.log2FAEnableFailed("alice", "Invalid TOTP code");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void log2FADisableFailed_publishesAuditEvent() {
        auditService.log2FADisableFailed("alice", "Cannot disable 2FA");
        verify(publisher).publish(any(AuditEvent.class));
    }

    // ─── OAuth failed variants ────────────────────────────────────────────────

    @Test
    void logOAuthLinkFailed_publishesAuditEvent() {
        auditService.logOAuthLinkFailed("alice", "google", "Already linked");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logOAuthUnlinkFailed_publishesAuditEvent() {
        auditService.logOAuthUnlinkFailed("alice", "google", "Cannot unlink last method");
        verify(publisher).publish(any(AuditEvent.class));
    }

    // ─── Token refresh ────────────────────────────────────────────────────────

    @Test
    void logTokenRefreshFailed_publishesAuditEvent() {
        auditService.logTokenRefreshFailed("Invalid refresh token");
        verify(publisher).publish(any(AuditEvent.class));
    }

    // ─── Permission events ────────────────────────────────────────────────────

    @Test
    void logPermissionCreated_publishesAuditEvent() {
        auditService.logPermissionCreated("test:read");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logPermissionBatchCreated_publishesAuditEvent() {
        auditService.logPermissionBatchCreated(5);
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logPermissionUpdated_publishesAuditEvent() {
        auditService.logPermissionUpdated("test:read");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logPermissionDeleted_publishesAuditEvent() {
        auditService.logPermissionDeleted("test:read");
        verify(publisher).publish(any(AuditEvent.class));
    }

    // ─── Role events ──────────────────────────────────────────────────────────

    @Test
    void logRoleCreated_publishesAuditEvent() {
        auditService.logRoleCreated("CUSTOM_ROLE");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logRoleBatchCreated_publishesAuditEvent() {
        auditService.logRoleBatchCreated(3);
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logRoleUpdated_publishesAuditEvent() {
        auditService.logRoleUpdated("HR");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logRoleDeleted_publishesAuditEvent() {
        auditService.logRoleDeleted("CUSTOM_ROLE");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logRoleCloned_publishesAuditEvent() {
        auditService.logRoleCloned("HR", "CUSTOM_HR");
        verify(publisher).publish(any(AuditEvent.class));
    }

    // ─── Role permission events ───────────────────────────────────────────────

    @Test
    void logRolePermissionAdded_publishesAuditEvent() {
        auditService.logRolePermissionAdded("HR", "hr:read");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logRolePermissionRemoved_publishesAuditEvent() {
        auditService.logRolePermissionRemoved("HR", "hr:read");
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logRolePermissionsAdded_publishesAuditEvent() {
        auditService.logRolePermissionsAdded("HR", 5);
        verify(publisher).publish(any(AuditEvent.class));
    }

    @Test
    void logRolePermissionsRemoved_publishesAuditEvent() {
        auditService.logRolePermissionsRemoved("HR", 3);
        verify(publisher).publish(any(AuditEvent.class));
    }

    // ─── extractRole / extractCompanyId with authenticated context ─────────────

    @Test
    void logLoginSuccess_withAuthenticatedContext_extractsRoleFromAuthorities() {
        // Set authenticated user with ADMIN role in SecurityContext
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "alice", null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ADMIN")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            auditService.logLoginSuccess("alice");
            verify(publisher).publish(any(AuditEvent.class));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void logPermissionCreated_withAuthenticatedUserHavingOnlyPermission_extractsSystemRole() {
        // Authority with ":" should not be selected as role (only permissions have ":")
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "alice", null,
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("perm:read")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        try {
            auditService.logPermissionCreated("perm:read");
            verify(publisher).publish(any(AuditEvent.class));
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    @Test
    void logLoginSuccess_withCompanyDetailsAndForwardedHeader_publishesActorMetadata() {
        var auth = new UsernamePasswordAuthenticationToken(
                "hrm",
                null,
                List.of(
                        new SimpleGrantedAuthority("HR_MANAGER"),
                        new SimpleGrantedAuthority("user:read")));
        auth.setDetails(Map.of("companyId", "company-123"));
        SecurityContextHolder.getContext().setAuthentication(auth);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", " 203.0.113.10, 10.0.0.1 ");
        request.setRemoteAddr("192.0.2.5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditService.logLoginSuccess("alice");

        AuditEvent event = publishedEvent();
        assertThat(event.actorRole()).isEqualTo("HR_MANAGER");
        assertThat(event.companyId()).isEqualTo("company-123");
        assertThat(event.actorIp()).isEqualTo("203.0.113.10");
    }

    @Test
    void logLoginSuccess_withoutForwardedHeader_usesRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditService.logLoginSuccess("alice");

        assertThat(publishedEvent().actorIp()).isEqualTo("192.0.2.5");
    }

    @Test
    void logLoginSuccess_withBlankForwardedHeader_usesRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "   ");
        request.setRemoteAddr("192.0.2.6");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        auditService.logLoginSuccess("alice");

        assertThat(publishedEvent().actorIp()).isEqualTo("192.0.2.6");
    }

    @Test
    void logPermissionCreated_withAnonymousPrincipal_usesSystemActor() {
        var auth = new UsernamePasswordAuthenticationToken(
                "anonymousUser",
                null,
                List.of(new SimpleGrantedAuthority("ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        auditService.logPermissionCreated("perm:read");

        AuditEvent event = publishedEvent();
        assertThat(event.actorUsername()).isEqualTo("system");
        assertThat(event.actorRole()).isNull();
    }

    @Test
    void logLoginSuccess_whenPublisherThrows_swallowsException() {
        doThrow(new IllegalStateException("kafka down")).when(publisher).publish(any(AuditEvent.class));

        assertThatCode(() -> auditService.logLoginSuccess("alice")).doesNotThrowAnyException();
    }

    private AuditEvent publishedEvent() {
        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(publisher).publish(captor.capture());
        return captor.getValue();
    }
}
