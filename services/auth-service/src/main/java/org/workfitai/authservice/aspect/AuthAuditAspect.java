package org.workfitai.authservice.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.workfitai.authservice.dto.request.Enable2FARequest;
import org.workfitai.authservice.dto.request.ForgotPasswordRequest;
import org.workfitai.authservice.dto.request.LoginRequest;
import org.workfitai.authservice.dto.request.RegisterRequest;
import org.workfitai.authservice.dto.request.VerifyOtpRequest;
import org.workfitai.authservice.enums.Provider;
import org.workfitai.authservice.service.AuthAuditService;

/**
 * AOP aspect for auth-service audit logging.
 * Intercepts service methods to write audit trail on both success and failure paths.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthAuditAspect {

    private final AuthAuditService authAuditService;

    // ─── Pointcuts ────────────────────────────────────────────────────────────

    @Pointcut("execution(* org.workfitai.authservice.service.impl.AuthServiceImpl.login(..))")
    public void loginPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.AuthServiceImpl.logout(..))")
    public void logoutPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.AuthServiceImpl.register(..))")
    public void registerPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.AuthServiceImpl.verifyOtp(..))")
    public void verifyOtpPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.AuthServiceImpl.verify2FALogin(..))")
    public void verify2FALoginPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.AuthServiceImpl.refresh(..))")
    public void tokenRefreshPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.PasswordService.changePassword(..))")
    public void changePasswordPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.PasswordService.forgotPassword(..))")
    public void forgotPasswordPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.PasswordService.verifyResetOtp(..))")
    public void verifyResetOtpPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.PasswordService.resetPassword(..))")
    public void resetPasswordPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.PasswordService.setPassword(..))")
    public void setPasswordPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.TwoFactorAuthService.enable2FA(..))")
    public void enable2FAPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.TwoFactorAuthService.disable2FA(..))")
    public void disable2FAPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.oauth.OAuthService.linkProvider(..))")
    public void oauthLinkPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.oauth.OAuthService.unlinkProvider(..))")
    public void oauthUnlinkPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.ApprovalServiceImpl.approveHRManager(..))")
    public void approveHRManagerPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.ApprovalServiceImpl.approveHR(..))")
    public void approveHRPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.impl.ApprovalServiceImpl.rejectUser(..))")
    public void rejectUserPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.SessionService.deleteSession(..))")
    public void deleteSessionPointcut() {}

    @Pointcut("execution(* org.workfitai.authservice.service.SessionService.deleteAllSessionsExceptCurrent(..))")
    public void deleteAllSessionsPointcut() {}

    // ─── Login ────────────────────────────────────────────────────────────────

    @AfterReturning(pointcut = "loginPointcut()")
    public void logLoginSuccess(JoinPoint jp) {
        try {
            String username = extractUsernameOrEmail(jp.getArgs()[0]);
            authAuditService.logLoginSuccess(username);
        } catch (Exception e) {
            log.error("Audit error on loginPointcut success", e);
        }
    }

    @AfterThrowing(pointcut = "loginPointcut()", throwing = "ex")
    public void logLoginFailed(JoinPoint jp, Throwable ex) {
        try {
            String username = extractUsernameOrEmail(jp.getArgs()[0]);
            authAuditService.logLoginFailed(username, ex.getMessage());
        } catch (Exception e) {
            log.error("Audit error on loginPointcut failure", e);
        }
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    @AfterReturning(pointcut = "logoutPointcut()")
    public void logLogout(JoinPoint jp) {
        try {
            // logout(deviceId, username) — username is args[1]
            String username = jp.getArgs().length >= 2 ? (String) jp.getArgs()[1] : "unknown";
            authAuditService.logLogout(username);
        } catch (Exception e) {
            log.error("Audit error on logoutPointcut", e);
        }
    }

    // ─── Register ─────────────────────────────────────────────────────────────

    @AfterReturning(pointcut = "registerPointcut()")
    public void logRegister(JoinPoint jp) {
        try {
            String email = extractEmail(jp.getArgs()[0]);
            authAuditService.logRegister(email);
        } catch (Exception e) {
            log.error("Audit error on registerPointcut success", e);
        }
    }

    @AfterThrowing(pointcut = "registerPointcut()", throwing = "ex")
    public void logRegisterFailed(JoinPoint jp, Throwable ex) {
        try {
            String email = extractEmail(jp.getArgs()[0]);
            authAuditService.logRegisterFailed(email, ex.getMessage());
        } catch (Exception e) {
            log.error("Audit error on registerPointcut failure", e);
        }
    }

    // ─── Verify OTP ───────────────────────────────────────────────────────────

    @AfterReturning(pointcut = "verifyOtpPointcut()")
    public void logOtpVerified(JoinPoint jp) {
        try {
            String email = extractEmail(jp.getArgs()[0]);
            authAuditService.logOtpVerified(email);
        } catch (Exception e) {
            log.error("Audit error on verifyOtpPointcut success", e);
        }
    }

    @AfterThrowing(pointcut = "verifyOtpPointcut()", throwing = "ex")
    public void logOtpVerifyFailed(JoinPoint jp, Throwable ex) {
        try {
            String email = extractEmail(jp.getArgs()[0]);
            authAuditService.logOtpVerifyFailed(email, ex.getMessage());
        } catch (Exception e) {
            log.error("Audit error on verifyOtpPointcut failure", e);
        }
    }

    // ─── Verify 2FA Login ─────────────────────────────────────────────────────

    @AfterReturning(pointcut = "verify2FALoginPointcut()", returning = "result")
    public void log2FALoginSuccess(JoinPoint jp, Object result) {
        try {
            // Verify2FALoginRequest has tempToken — use SecurityContext username if possible
            String identity = extractCurrentPrincipal();
            authAuditService.log2FALoginSuccess(identity);
        } catch (Exception e) {
            log.error("Audit error on verify2FALoginPointcut success", e);
        }
    }

    @AfterThrowing(pointcut = "verify2FALoginPointcut()", throwing = "ex")
    public void log2FALoginFailed(JoinPoint jp, Throwable ex) {
        try {
            String identity = extractCurrentPrincipal();
            authAuditService.log2FALoginFailed(identity, ex.getMessage());
        } catch (Exception e) {
            log.error("Audit error on verify2FALoginPointcut failure", e);
        }
    }

    // ─── Token Refresh ────────────────────────────────────────────────────────

    @AfterReturning(pointcut = "tokenRefreshPointcut()")
    public void logTokenRefreshed(JoinPoint jp) {
        try {
            String username = extractCurrentPrincipal();
            authAuditService.logTokenRefreshed(username);
        } catch (Exception e) {
            log.error("Audit error on tokenRefreshPointcut success", e);
        }
    }

    @AfterThrowing(pointcut = "tokenRefreshPointcut()", throwing = "ex")
    public void logTokenRefreshFailed(JoinPoint jp, Throwable ex) {
        try {
            authAuditService.logTokenRefreshFailed(ex.getMessage());
        } catch (Exception e) {
            log.error("Audit error on tokenRefreshPointcut failure", e);
        }
    }

    // ─── Change Password ──────────────────────────────────────────────────────

    @AfterReturning(pointcut = "changePasswordPointcut()")
    public void logPasswordChanged(JoinPoint jp) {
        try {
            String username = (String) jp.getArgs()[0];
            authAuditService.logPasswordChanged(username);
        } catch (Exception e) {
            log.error("Audit error on changePasswordPointcut success", e);
        }
    }

    @AfterThrowing(pointcut = "changePasswordPointcut()", throwing = "ex")
    public void logPasswordChangeFailed(JoinPoint jp, Throwable ex) {
        try {
            String username = (String) jp.getArgs()[0];
            authAuditService.logPasswordChangeFailed(username, ex.getMessage());
        } catch (Exception e) {
            log.error("Audit error on changePasswordPointcut failure", e);
        }
    }

    // ─── Forgot Password ──────────────────────────────────────────────────────

    @AfterReturning(pointcut = "forgotPasswordPointcut()")
    public void logForgotPassword(JoinPoint jp) {
        try {
            String email = jp.getArgs()[0] instanceof ForgotPasswordRequest req ? req.getEmail() : "unknown";
            authAuditService.logForgotPasswordRequested(email);
        } catch (Exception e) {
            log.error("Audit error on forgotPasswordPointcut", e);
        }
    }

    // ─── Verify Reset OTP ─────────────────────────────────────────────────────

    @AfterReturning(pointcut = "verifyResetOtpPointcut()")
    public void logPasswordResetOtpVerified(JoinPoint jp) {
        try {
            String email = extractEmail(jp.getArgs()[0]);
            authAuditService.logPasswordResetOtpVerified(email);
        } catch (Exception e) {
            log.error("Audit error on verifyResetOtpPointcut success", e);
        }
    }

    @AfterThrowing(pointcut = "verifyResetOtpPointcut()", throwing = "ex")
    public void logPasswordResetOtpFailed(JoinPoint jp, Throwable ex) {
        try {
            String email = extractEmail(jp.getArgs()[0]);
            authAuditService.logPasswordResetOtpFailed(email, ex.getMessage());
        } catch (Exception e) {
            log.error("Audit error on verifyResetOtpPointcut failure", e);
        }
    }

    // ─── Reset Password ───────────────────────────────────────────────────────

    @AfterReturning(pointcut = "resetPasswordPointcut()")
    public void logPasswordReset(JoinPoint jp) {
        try {
            String principal = extractCurrentPrincipal();
            authAuditService.logPasswordReset(principal);
        } catch (Exception e) {
            log.error("Audit error on resetPasswordPointcut success", e);
        }
    }

    @AfterThrowing(pointcut = "resetPasswordPointcut()", throwing = "ex")
    public void logPasswordResetFailed(JoinPoint jp, Throwable ex) {
        try {
            authAuditService.logPasswordResetFailed(ex.getMessage());
        } catch (Exception e) {
            log.error("Audit error on resetPasswordPointcut failure", e);
        }
    }

    // ─── Set Password ─────────────────────────────────────────────────────────

    @AfterReturning(pointcut = "setPasswordPointcut()")
    public void logPasswordSet(JoinPoint jp) {
        try {
            String username = (String) jp.getArgs()[0];
            authAuditService.logPasswordSet(username);
        } catch (Exception e) {
            log.error("Audit error on setPasswordPointcut success", e);
        }
    }

    @AfterThrowing(pointcut = "setPasswordPointcut()", throwing = "ex")
    public void logPasswordSetFailed(JoinPoint jp, Throwable ex) {
        try {
            String username = (String) jp.getArgs()[0];
            authAuditService.logPasswordSetFailed(username, ex.getMessage());
        } catch (Exception e) {
            log.error("Audit error on setPasswordPointcut failure", e);
        }
    }

    // ─── Enable 2FA ───────────────────────────────────────────────────────────

    @AfterReturning(pointcut = "enable2FAPointcut()")
    public void log2FAEnabled(JoinPoint jp) {
        try {
            String username = (String) jp.getArgs()[0];
            String method = jp.getArgs().length >= 2 && jp.getArgs()[1] instanceof Enable2FARequest req
                    ? (req.getMethod() != null ? req.getMethod() : "TOTP")
                    : "TOTP";
            authAuditService.log2FAEnabled(username, method);
        } catch (Exception e) {
            log.error("Audit error on enable2FAPointcut success", e);
        }
    }

    @AfterThrowing(pointcut = "enable2FAPointcut()", throwing = "ex")
    public void log2FAEnableFailed(JoinPoint jp, Throwable ex) {
        try {
            String username = (String) jp.getArgs()[0];
            authAuditService.log2FAEnableFailed(username, ex.getMessage());
        } catch (Exception e) {
            log.error("Audit error on enable2FAPointcut failure", e);
        }
    }

    // ─── Disable 2FA ──────────────────────────────────────────────────────────

    @AfterReturning(pointcut = "disable2FAPointcut()")
    public void log2FADisabled(JoinPoint jp) {
        try {
            String username = (String) jp.getArgs()[0];
            authAuditService.log2FADisabled(username);
        } catch (Exception e) {
            log.error("Audit error on disable2FAPointcut success", e);
        }
    }

    @AfterThrowing(pointcut = "disable2FAPointcut()", throwing = "ex")
    public void log2FADisableFailed(JoinPoint jp, Throwable ex) {
        try {
            String username = (String) jp.getArgs()[0];
            authAuditService.log2FADisableFailed(username, ex.getMessage());
        } catch (Exception e) {
            log.error("Audit error on disable2FAPointcut failure", e);
        }
    }

    // ─── OAuth Link ───────────────────────────────────────────────────────────

    @AfterReturning(pointcut = "oauthLinkPointcut()")
    public void logOAuthLinked(JoinPoint jp) {
        try {
            String username = (String) jp.getArgs()[0];
            String provider = jp.getArgs()[1] instanceof Provider p ? p.name() : "unknown";
            authAuditService.logOAuthLinked(username, provider);
        } catch (Exception e) {
            log.error("Audit error on oauthLinkPointcut success", e);
        }
    }

    @AfterThrowing(pointcut = "oauthLinkPointcut()", throwing = "ex")
    public void logOAuthLinkFailed(JoinPoint jp, Throwable ex) {
        try {
            String username = (String) jp.getArgs()[0];
            String provider = jp.getArgs()[1] instanceof Provider p ? p.name() : "unknown";
            authAuditService.logOAuthLinkFailed(username, provider, ex.getMessage());
        } catch (Exception e) {
            log.error("Audit error on oauthLinkPointcut failure", e);
        }
    }

    // ─── OAuth Unlink ─────────────────────────────────────────────────────────

    @AfterReturning(pointcut = "oauthUnlinkPointcut()")
    public void logOAuthUnlinked(JoinPoint jp) {
        try {
            String username = (String) jp.getArgs()[0];
            String provider = jp.getArgs()[1] instanceof Provider p ? p.name() : "unknown";
            authAuditService.logOAuthUnlinked(username, provider);
        } catch (Exception e) {
            log.error("Audit error on oauthUnlinkPointcut success", e);
        }
    }

    @AfterThrowing(pointcut = "oauthUnlinkPointcut()", throwing = "ex")
    public void logOAuthUnlinkFailed(JoinPoint jp, Throwable ex) {
        try {
            String username = (String) jp.getArgs()[0];
            String provider = jp.getArgs()[1] instanceof Provider p ? p.name() : "unknown";
            authAuditService.logOAuthUnlinkFailed(username, provider, ex.getMessage());
        } catch (Exception e) {
            log.error("Audit error on oauthUnlinkPointcut failure", e);
        }
    }

    // ─── Approval: HR Manager ─────────────────────────────────────────────────

    @AfterReturning(pointcut = "approveHRManagerPointcut()")
    public void logHRManagerApproved(JoinPoint jp) {
        try {
            String userId = (String) jp.getArgs()[0];
            String approvedBy = (String) jp.getArgs()[1];
            authAuditService.logHRManagerApproved(approvedBy, userId);
        } catch (Exception e) {
            log.error("Audit error on approveHRManagerPointcut", e);
        }
    }

    // ─── Approval: HR ─────────────────────────────────────────────────────────

    @AfterReturning(pointcut = "approveHRPointcut()")
    public void logHRApproved(JoinPoint jp) {
        try {
            String userId = (String) jp.getArgs()[0];
            String approvedBy = (String) jp.getArgs()[1];
            authAuditService.logHRApproved(approvedBy, userId);
        } catch (Exception e) {
            log.error("Audit error on approveHRPointcut", e);
        }
    }

    // ─── Rejection ────────────────────────────────────────────────────────────

    @AfterReturning(pointcut = "rejectUserPointcut()")
    public void logUserRejected(JoinPoint jp) {
        try {
            String userId = (String) jp.getArgs()[0];
            String rejectedBy = (String) jp.getArgs()[1];
            String reason = jp.getArgs().length >= 3 ? (String) jp.getArgs()[2] : null;
            authAuditService.logUserRejected(rejectedBy, userId, reason);
        } catch (Exception e) {
            log.error("Audit error on rejectUserPointcut", e);
        }
    }

    // ─── Sessions ─────────────────────────────────────────────────────────────

    @AfterReturning(pointcut = "deleteSessionPointcut()")
    public void logSessionDeleted(JoinPoint jp) {
        try {
            // deleteSession(userId, sessionId, currentSessionId)
            String userId = (String) jp.getArgs()[0];
            String sessionId = (String) jp.getArgs()[1];
            authAuditService.logSessionDeleted(userId, sessionId);
        } catch (Exception e) {
            log.error("Audit error on deleteSessionPointcut", e);
        }
    }

    @AfterReturning(pointcut = "deleteAllSessionsPointcut()")
    public void logAllSessionsDeleted(JoinPoint jp) {
        try {
            String userId = (String) jp.getArgs()[0];
            authAuditService.logAllSessionsDeleted(userId);
        } catch (Exception e) {
            log.error("Audit error on deleteAllSessionsPointcut", e);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String extractUsernameOrEmail(Object arg) {
        if (arg instanceof LoginRequest req) {
            return req.getUsernameOrEmail() != null ? req.getUsernameOrEmail() : "unknown";
        }
        return "unknown";
    }

    private String extractEmail(Object arg) {
        if (arg instanceof RegisterRequest req) {
            return req.getEmail() != null ? req.getEmail() : "unknown";
        }
        if (arg instanceof VerifyOtpRequest req) {
            return req.getEmail() != null ? req.getEmail() : "unknown";
        }
        return "unknown";
    }

    private String extractCurrentPrincipal() {
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            return (auth != null && auth.isAuthenticated()) ? auth.getName() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}
