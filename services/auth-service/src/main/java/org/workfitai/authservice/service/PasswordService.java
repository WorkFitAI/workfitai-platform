package org.workfitai.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.workfitai.authservice.config.PasswordPolicyConfig;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.dto.kafka.NotificationEvent;
import org.workfitai.authservice.dto.request.ChangePasswordRequest;
import org.workfitai.authservice.dto.request.ForgotPasswordRequest;
import org.workfitai.authservice.dto.request.ResetPasswordRequest;
import org.workfitai.authservice.dto.response.PasswordResetResponse;
import org.workfitai.authservice.exception.BadRequestException;
import org.workfitai.authservice.exception.NotFoundException;
import org.workfitai.authservice.exception.TooManyRequestsException;
import org.workfitai.authservice.model.PasswordResetToken;
import org.workfitai.authservice.repository.PasswordResetTokenRepository;
import org.workfitai.authservice.repository.UserRepository;

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
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyConfig passwordPolicy;
    private final RedisTemplate<String, String> redisTemplate;
    private final NotificationProducer notificationProducer;
    private final RefreshTokenService refreshTokenService;

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
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }

        // Check if new password is same as current
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from current password");
        }

        // Update password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        // Invalidate all refresh tokens (logout from all devices)
        refreshTokenService.deleteAllByUsername(username);

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
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        // Mark token as used
        resetToken.setUsed(true);
        resetToken.setUsedAt(LocalDateTime.now());
        resetTokenRepository.save(resetToken);

        // Invalidate all refresh tokens
        refreshTokenService.deleteAllByUsername(user.getUsername());

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

        NotificationEvent event = NotificationEvent.builder()
                .recipientEmail(user.getEmail())
                .templateType("PASSWORD_RESET_OTP")
                .data(data)
                .build();

        notificationProducer.send(event);
    }

    private void sendPasswordChangedNotification(User user) {
        Map<String, Object> data = new HashMap<>();
        data.put("username", user.getUsername());
        data.put("fullName", user.getFullName());
        data.put("changedAt", LocalDateTime.now().toString());

        NotificationEvent event = NotificationEvent.builder()
                .recipientEmail(user.getEmail())
                .templateType("PASSWORD_CHANGED")
                .data(data)
                .build();

        notificationProducer.send(event);
    }

    private void sendPasswordResetSuccessNotification(User user) {
        Map<String, Object> data = new HashMap<>();
        data.put("username", user.getUsername());
        data.put("fullName", user.getFullName());
        data.put("resetAt", LocalDateTime.now().toString());

        NotificationEvent event = NotificationEvent.builder()
                .recipientEmail(user.getEmail())
                .templateType("PASSWORD_RESET_SUCCESS")
                .data(data)
                .build();

        notificationProducer.send(event);
    }
}
