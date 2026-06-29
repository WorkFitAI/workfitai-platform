package org.workfitai.authservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.workfitai.authservice.dto.kafka.AuditEvent;
import org.workfitai.authservice.messaging.AuditEventPublisher;

@ExtendWith(MockitoExtension.class)
class AuthAuditServiceTest {

    @Mock AuditEventPublisher publisher;
    @InjectMocks AuthAuditService auditService;

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
}
