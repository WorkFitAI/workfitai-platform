package org.workfitai.authservice.service;

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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes auth-service audit events to the Kafka "audit-events" topic.
 *
 * Covers: login, logout, register, OTP verification, 2FA, OAuth, password operations,
 * session management, and approval flows. Events are consumed by monitoring-service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthAuditService {

    private final AuditEventPublisher publisher;

    // ─── Auth events ─────────────────────────────────────────────────────────────

    public void logLoginSuccess(String username) {
        publish("USER", username, "AUTH_LOGIN_SUCCESS", username, null,
                Map.of("loginAt", Instant.now().toString()), true, null);
    }

    public void logLoginFailed(String username, String errorMessage) {
        publish("USER", username, "AUTH_LOGIN_FAILED", username, null, null, false, errorMessage);
    }

    public void logLogout(String username) {
        publish("USER", username, "AUTH_LOGOUT", username,
                null, Map.of("logoutAt", Instant.now().toString()), true, null);
    }

    public void logRegister(String username) {
        publish("USER", username, "AUTH_REGISTER", username,
                null, Map.of("registeredAt", Instant.now().toString()), true, null);
    }

    public void logRegisterFailed(String username, String errorMessage) {
        publish("USER", username, "AUTH_REGISTER", username, null, null, false, errorMessage);
    }

    public void logOtpVerified(String username) {
        publish("USER", username, "AUTH_OTP_VERIFIED", username,
                Map.of("otpVerified", false), Map.of("otpVerified", true), true, null);
    }

    public void logOtpVerifyFailed(String email, String errorMessage) {
        publish("USER", email, "AUTH_OTP_VERIFIED", email, null, null, false, errorMessage);
    }

    public void log2FALoginSuccess(String username) {
        publish("USER", username, "AUTH_2FA_LOGIN_SUCCESS", username,
                null, Map.of("verifiedAt", Instant.now().toString()), true, null);
    }

    public void log2FALoginFailed(String username, String errorMessage) {
        publish("USER", username, "AUTH_2FA_LOGIN_FAILED", username, null, null, false, errorMessage);
    }

    public void logTokenRefreshed(String username) {
        publish("USER", username, "AUTH_TOKEN_REFRESHED", username,
                null, Map.of("refreshedAt", Instant.now().toString()), true, null);
    }

    public void logTokenRefreshFailed(String errorMessage) {
        publish("USER", "unknown", "AUTH_TOKEN_REFRESHED", "unknown", null, null, false, errorMessage);
    }

    // ─── Password events ──────────────────────────────────────────────────────────

    public void logPasswordChanged(String username) {
        publish("USER", username, "AUTH_PASSWORD_CHANGED", username,
                null, Map.of("changedAt", Instant.now().toString()), true, null);
    }

    public void logPasswordChangeFailed(String username, String errorMessage) {
        publish("USER", username, "AUTH_PASSWORD_CHANGED", username, null, null, false, errorMessage);
    }

    public void logForgotPasswordRequested(String email) {
        publish("USER", email, "AUTH_PASSWORD_FORGOT_REQUESTED", email,
                null, Map.of("requestedAt", Instant.now().toString()), true, null);
    }

    public void logPasswordResetOtpVerified(String email) {
        publish("USER", email, "AUTH_PASSWORD_RESET_OTP_VERIFIED", email,
                null, Map.of("verifiedAt", Instant.now().toString()), true, null);
    }

    public void logPasswordResetOtpFailed(String email, String errorMessage) {
        publish("USER", email, "AUTH_PASSWORD_RESET_OTP_VERIFIED", email, null, null, false, errorMessage);
    }

    public void logPasswordReset(String username) {
        publish("USER", username, "AUTH_PASSWORD_RESET", username,
                null, Map.of("resetAt", Instant.now().toString()), true, null);
    }

    public void logPasswordResetFailed(String errorMessage) {
        publish("USER", "unknown", "AUTH_PASSWORD_RESET", "unknown", null, null, false, errorMessage);
    }

    public void logPasswordSet(String username) {
        publish("USER", username, "AUTH_PASSWORD_SET", username,
                Map.of("hasPassword", false), Map.of("hasPassword", true), true, null);
    }

    public void logPasswordSetFailed(String username, String errorMessage) {
        publish("USER", username, "AUTH_PASSWORD_SET", username, null, null, false, errorMessage);
    }

    // ─── 2FA events ───────────────────────────────────────────────────────────────

    public void log2FAEnabled(String username, String method) {
        publish("USER", username, "AUTH_2FA_ENABLED", username,
                Map.of("2faEnabled", false), Map.of("2faEnabled", true, "method", method), true, null);
    }

    public void log2FAEnableFailed(String username, String errorMessage) {
        publish("USER", username, "AUTH_2FA_ENABLED", username, null, null, false, errorMessage);
    }

    public void log2FADisabled(String username) {
        publish("USER", username, "AUTH_2FA_DISABLED", username,
                Map.of("2faEnabled", true), Map.of("2faEnabled", false), true, null);
    }

    public void log2FADisableFailed(String username, String errorMessage) {
        publish("USER", username, "AUTH_2FA_DISABLED", username, null, null, false, errorMessage);
    }

    // ─── OAuth events ─────────────────────────────────────────────────────────────

    public void logOAuthLinked(String username, String provider) {
        publish("OAUTH", username, "AUTH_OAUTH_LINKED", username,
                null, Map.of("provider", provider, "linkedAt", Instant.now().toString()), true, null);
    }

    public void logOAuthLinkFailed(String username, String provider, String errorMessage) {
        publish("OAUTH", username, "AUTH_OAUTH_LINKED", username,
                null, Map.of("provider", provider), false, errorMessage);
    }

    public void logOAuthUnlinked(String username, String provider) {
        publish("OAUTH", username, "AUTH_OAUTH_UNLINKED", username,
                Map.of("provider", provider), Map.of("unlinkedAt", Instant.now().toString()), true, null);
    }

    public void logOAuthUnlinkFailed(String username, String provider, String errorMessage) {
        publish("OAUTH", username, "AUTH_OAUTH_UNLINKED", username,
                Map.of("provider", provider), null, false, errorMessage);
    }

    // ─── Approval events ──────────────────────────────────────────────────────────

    public void logHRManagerApproved(String approvedBy, String targetUserId) {
        publish("USER", targetUserId, "USER_APPROVED_HR_MANAGER", approvedBy,
                Map.of("status", "PENDING"), Map.of("status", "APPROVED", "role", "HR_MANAGER"), true, null);
    }

    public void logHRApproved(String approvedBy, String targetUserId) {
        publish("USER", targetUserId, "USER_APPROVED_HR", approvedBy,
                Map.of("status", "PENDING"), Map.of("status", "APPROVED", "role", "HR"), true, null);
    }

    public void logUserRejected(String rejectedBy, String targetUserId, String reason) {
        publish("USER", targetUserId, "USER_REJECTED", rejectedBy,
                Map.of("status", "PENDING"),
                Map.of("status", "REJECTED", "reason", reason != null ? reason : ""),
                true, null);
    }

    // ─── Session events ───────────────────────────────────────────────────────────

    public void logSessionDeleted(String username, String sessionId) {
        publish("SESSION", sessionId, "AUTH_SESSION_DELETED", username,
                Map.of("sessionId", sessionId), Map.of("deletedAt", Instant.now().toString()), true, null);
    }

    public void logAllSessionsDeleted(String username) {
        publish("SESSION", username, "AUTH_ALL_SESSIONS_DELETED", username,
                null, Map.of("deletedAt", Instant.now().toString()), true, null);
    }

    // ─── Internal publish ─────────────────────────────────────────────────────────

    private void publish(
            String entityType,
            String entityId,
            String action,
            String actorUsername,
            Map<String, Object> before,
            Map<String, Object> after,
            Boolean success,
            String errorMessage
    ) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String actorRole = extractRole(auth);
            String companyId = extractCompanyId(auth);

            publisher.publish(new AuditEvent(
                    UUID.randomUUID().toString(),
                    "auth-service",
                    actorUsername,
                    actorRole,
                    companyId,
                    entityType,
                    entityId,
                    action,
                    before,
                    after,
                    Instant.now(),
                    success,
                    errorMessage,
                    extractClientIp()
            ));
        } catch (Exception ex) {
            log.error("[AUDIT] Failed to publish {} for {}: {}", action, entityId, ex.getMessage());
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
            return null; // non-request context (async, scheduled)
        }
    }
}
