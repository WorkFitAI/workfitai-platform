package org.workfitai.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.authservice.config.PasswordPolicyConfig;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.dto.kafka.NotificationEvent;
import org.workfitai.authservice.dto.kafka.PasswordChangeEvent;
import org.workfitai.authservice.dto.request.ChangePasswordRequest;
import org.workfitai.authservice.dto.request.ForgotPasswordRequest;
import org.workfitai.authservice.dto.request.ResetPasswordRequest;
import org.workfitai.authservice.dto.response.PasswordResetResponse;
import org.workfitai.authservice.exception.BadRequestException;
import org.workfitai.authservice.exception.NotFoundException;
import org.workfitai.authservice.exception.TooManyRequestsException;
import org.workfitai.authservice.messaging.NotificationProducer;
import org.workfitai.authservice.model.PasswordResetToken;
import org.workfitai.authservice.model.UserSession;
import org.workfitai.authservice.repository.PasswordResetTokenRepository;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.repository.UserSessionRepository;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Service
@RefreshScope
@RequiredArgsConstructor
public class PasswordService {

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyConfig passwordPolicy;
    private final RedisTemplate<String, String> redisTemplate;
    private final NotificationProducer notificationProducer;
    private final PasswordChangeProducer passwordChangeProducer;
    private final RefreshTokenService refreshTokenService;
    private final GeoLocationService geoLocationService;
    private final HttpServletRequest request;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    private static final String CHANGE_PASSWORD_RATE_LIMIT_KEY = "password:change:";
    private static final String FORGOT_PASSWORD_RATE_LIMIT_KEY = "password:forgot:";

    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        // Check rate limit
        checkChangePasswordRateLimit(username);

        // Validate password match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        // Validate password policy
        validatePasswordPolicy(request.getNewPassword());

        // Get user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        // Check if new password is same as current
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("New password must be different from current password");
        }

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);

        // Invalidate all refresh tokens (logout from all devices)
        // Use userId instead of username to match the key format:
        // auth:rt:{userId}:{deviceId}
        refreshTokenService.deleteAllByUserId(user.getId());

        // Delete all sessions to invalidate all JWTs
        sessionRepository.deleteByUserId(user.getId());
        log.info("Deleted all sessions for user {} after password change", username);

        // Publish password change event to sync with user-service
        publishPasswordChangeEvent(user, "USER_CHANGE");

        // Increment rate limit counter
        incrementChangePasswordCounter(username);

        // Send notification
        sendPasswordChangedNotification(user);

        log.info("Password changed successfully for user: {}", username);
    }

    @Transactional
    public PasswordResetResponse forgotPassword(ForgotPasswordRequest request) {
        // Check rate limit
        checkForgotPasswordRateLimit(request.getEmail());

        // Find user by email
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new NotFoundException("User not found with email: " + request.getEmail()));

        // Delete existing reset tokens for this email
        resetTokenRepository.deleteByEmail(request.getEmail());

        // Generate reset token and OTP
        String token = UUID.randomUUID().toString();
        String otp = generateOTP();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);

        // Save reset token
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .email(request.getEmail())
                .token(token)
                .otp(otp)
                .createdAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .used(false)
                .attempts(0)
                .build();
        resetTokenRepository.save(resetToken);

        // Send OTP via email
        sendPasswordResetOTP(user, otp, expiresAt);

        // Increment rate limit counter
        incrementForgotPasswordCounter(request.getEmail());

        log.info("Password reset OTP sent to: {}", request.getEmail());

        return PasswordResetResponse.builder()
                .message("Password reset OTP sent to your email")
                .token(token)
                .expiresAt(expiresAt)
                .email(maskEmail(request.getEmail()))
                .build();
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        // Validate password match
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match");
        }

        // Validate password policy
        validatePasswordPolicy(request.getNewPassword());

        // Find valid reset token
        PasswordResetToken resetToken = resetTokenRepository
                .findByTokenAndUsedFalseAndExpiresAtAfter(request.getToken(), LocalDateTime.now())
                .orElseThrow(() -> new BadRequestException("Invalid or expired reset token"));

        // Check attempts
        if (resetToken.getAttempts() >= 5) {
            throw new BadRequestException("Too many failed attempts. Please request a new reset token");
        }

        // Verify OTP
        if (!resetToken.getOtp().equals(request.getOtp())) {
            resetToken.setAttempts(resetToken.getAttempts() + 1);
            resetTokenRepository.save(resetToken);
            throw new BadRequestException("Invalid OTP code");
        }

        // Find user
        User user = userRepository.findByEmail(resetToken.getEmail())
                .orElseThrow(() -> new NotFoundException("User not found"));

        // Update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);

        // Mark token as used
        resetToken.setUsed(true);
        resetToken.setUsedAt(LocalDateTime.now());
        resetTokenRepository.save(resetToken);

        // Invalidate all refresh tokens (use userId to match key format)
        refreshTokenService.deleteAllByUserId(user.getId());

        // Delete all sessions to invalidate all JWTs
        sessionRepository.deleteByUserId(user.getId());
        log.info("Deleted all sessions for user {} after password reset", user.getUsername());

        // Publish password change event to sync with user-service
        publishPasswordChangeEvent(user, "PASSWORD_RESET");

        // Send notification
        sendPasswordResetSuccessNotification(user);

        log.info("Password reset successfully for user: {}", user.getUsername());
    }

    private void validatePasswordPolicy(String password) {
        if (password.length() < passwordPolicy.getMinLength()) {
            throw new BadRequestException("Password must be at least " + passwordPolicy.getMinLength() + " characters");
        }

        if (passwordPolicy.getRequireUppercase() && !Pattern.compile("[A-Z]").matcher(password).find()) {
            throw new BadRequestException("Password must contain at least one uppercase letter");
        }

        if (passwordPolicy.getRequireLowercase() && !Pattern.compile("[a-z]").matcher(password).find()) {
            throw new BadRequestException("Password must contain at least one lowercase letter");
        }

        if (passwordPolicy.getRequireDigit() && !Pattern.compile("\\d").matcher(password).find()) {
            throw new BadRequestException("Password must contain at least one digit");
        }

        if (passwordPolicy.getRequireSpecialChar()) {
            String specialCharsRegex = "[" + Pattern.quote(passwordPolicy.getSpecialChars()) + "]";
            if (!Pattern.compile(specialCharsRegex).matcher(password).find()) {
                throw new BadRequestException("Password must contain at least one special character (" +
                        passwordPolicy.getSpecialChars() + ")");
            }
        }
    }

    private void checkChangePasswordRateLimit(String username) {
        String key = CHANGE_PASSWORD_RATE_LIMIT_KEY + username;
        String count = redisTemplate.opsForValue().get(key);

        if (count != null && Integer.parseInt(count) >= passwordPolicy.getChangeRateLimit()) {
            throw new TooManyRequestsException("Too many password change attempts. Please try again later");
        }
    }

    private void incrementChangePasswordCounter(String username) {
        String key = CHANGE_PASSWORD_RATE_LIMIT_KEY + username;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, passwordPolicy.getChangeRateWindow(), TimeUnit.SECONDS);
    }

    private void checkForgotPasswordRateLimit(String email) {
        String key = FORGOT_PASSWORD_RATE_LIMIT_KEY + email;
        String count = redisTemplate.opsForValue().get(key);

        if (count != null && Integer.parseInt(count) >= passwordPolicy.getForgotRateLimit()) {
            throw new TooManyRequestsException("Too many password reset requests. Please try again later");
        }
    }

    private void incrementForgotPasswordCounter(String email) {
        String key = FORGOT_PASSWORD_RATE_LIMIT_KEY + email;
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, passwordPolicy.getForgotRateWindow(), TimeUnit.SECONDS);
    }

    private String generateOTP() {
        Random random = new Random();
        return String.format("%06d", random.nextInt(1000000));
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 2)
            return email;

        String username = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        String masked = username.substring(0, 2) + "***";
        return masked + domain;
    }

    private void sendPasswordResetOTP(User user, String otp, LocalDateTime expiresAt) {
        Map<String, Object> data = new HashMap<>();
        data.put("otp", otp);
        data.put("fullName", user.getFullName());
        data.put("validUntil", expiresAt.toString());
        data.put("resetUrl", frontendBaseUrl + "/reset-password");

        NotificationEvent event = NotificationEvent.builder()
                .recipientEmail(user.getEmail())
                .templateType("password-reset")
                .sendEmail(true)
                .createInAppNotification(false)
                .metadata(data)
                .build();

        notificationProducer.send(event);
    }

    /**
     * Publish password change event to Kafka for user-service to sync passwordHash.
     * Uses fire-and-forget pattern - does not block password change operation.
     */
    private void publishPasswordChangeEvent(User user, String changeReason) {
        PasswordChangeEvent event = PasswordChangeEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("PASSWORD_CHANGED")
                .timestamp(Instant.now())
                .passwordData(PasswordChangeEvent.PasswordData.builder()
                        .userId(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .newPasswordHash(user.getPassword())
                        .passwordChangedAt(user.getPasswordChangedAt())
                        .changeReason(changeReason)
                        .build())
                .build();

        passwordChangeProducer.publishPasswordChangeEvent(event);
        log.info("Published password change event for user: {} (reason: {})", user.getUsername(), changeReason);
    }

    private void sendPasswordChangedNotification(User user) {
        // Extract device and location info from current request
        String ipAddress = getClientIpAddress();
        String userAgent = getUserAgent();
        String deviceName = extractDeviceName(userAgent);
        UserSession.Location location = geoLocationService.getLocation(ipAddress);
        String locationString = formatLocation(location);

        Map<String, Object> data = new HashMap<>();
        data.put("username", user.getUsername());
        data.put("changedAt", LocalDateTime.now().toString());
        data.put("deviceInfo", deviceName);
        data.put("ipAddress", ipAddress);
        data.put("location", locationString);
        data.put("loginUrl", frontendBaseUrl + "/login");

        NotificationEvent event = NotificationEvent.builder()
                .recipientEmail(user.getEmail())
                .templateType("password-changed")
                .sendEmail(true)
                .createInAppNotification(true)
                .metadata(data)
                .build();

        notificationProducer.send(event);
    }

    private void sendPasswordResetSuccessNotification(User user) {
        // Reuse password-changed template since it's the same notification
        sendPasswordChangedNotification(user);
    }

    /* ========== Request Context Utilities ========== */

    private String getClientIpAddress() {
        if (request == null) {
            return "Unknown IP";
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "Unknown IP";
    }

    private String getUserAgent() {
        if (request == null) {
            return "Unknown";
        }

        String userAgent = request.getHeader("User-Agent");
        return userAgent != null ? userAgent : "Unknown";
    }

    private String extractDeviceName(String userAgent) {
        if (userAgent == null || userAgent.isEmpty() || "Unknown".equals(userAgent)) {
            return "Unknown Device";
        }

        // Mobile device detection
        if (userAgent.contains("Mobile")) {
            if (userAgent.contains("iPhone"))
                return "iPhone";
            if (userAgent.contains("iPad"))
                return "iPad";
            if (userAgent.contains("Android"))
                return "Android Phone";
            return "Mobile Device";
        }

        // Desktop OS detection
        if (userAgent.contains("Windows"))
            return "Windows PC";
        if (userAgent.contains("Macintosh") || userAgent.contains("Mac OS"))
            return "Mac";
        if (userAgent.contains("Linux"))
            return "Linux PC";

        // Browser detection fallback
        if (userAgent.contains("Chrome"))
            return "Chrome Browser";
        if (userAgent.contains("Firefox"))
            return "Firefox Browser";
        if (userAgent.contains("Safari"))
            return "Safari Browser";
        if (userAgent.contains("Edge"))
            return "Edge Browser";

        return "Unknown Device";
    }

    private String formatLocation(UserSession.Location location) {
        if (location == null) {
            return "Unknown Location";
        }

        StringBuilder sb = new StringBuilder();

        if (location.getCity() != null && !location.getCity().isEmpty()) {
            sb.append(location.getCity());
        }

        if (location.getRegion() != null && !location.getRegion().isEmpty()) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(location.getRegion());
        }

        if (location.getCountry() != null && !location.getCountry().isEmpty()) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(location.getCountry());
        }

        return sb.length() > 0 ? sb.toString() : "Unknown Location";
    }
}
