package org.workfitai.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.workfitai.authservice.config.PasswordPolicyConfig;
import org.workfitai.authservice.dto.request.ChangePasswordRequest;
import org.workfitai.authservice.dto.request.ForgotPasswordRequest;
import org.workfitai.authservice.dto.request.ResetPasswordRequest;
import org.workfitai.authservice.dto.request.VerifyOtpRequest;
import org.workfitai.authservice.enums.UserStatus;
import org.workfitai.authservice.exception.BadRequestException;
import org.workfitai.authservice.exception.NotFoundException;
import org.workfitai.authservice.messaging.NotificationProducer;
import org.workfitai.authservice.model.PasswordResetToken;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.PasswordResetTokenRepository;
import org.workfitai.authservice.repository.UserRepository;
import org.workfitai.authservice.repository.UserSessionRepository;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserSessionRepository sessionRepository;
    @Mock PasswordResetTokenRepository resetTokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock PasswordPolicyConfig passwordPolicy;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock NotificationProducer notificationProducer;
    @Mock PasswordChangeProducer passwordChangeProducer;
    @Mock RefreshTokenService refreshTokenService;
    @Mock GeoLocationService geoLocationService;
    @Mock HttpServletRequest httpRequest;

    @InjectMocks PasswordService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "frontendBaseUrl", "http://localhost:3000");
        Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    private User activeUser() {
        User u = new User();
        u.setId("user-id-1");
        u.setUsername("alice");
        u.setEmail("alice@example.com");
        u.setPassword("hashed-current");
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    private ChangePasswordRequest changeReq(String current, String newPwd, String confirm) {
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.setCurrentPassword(current);
        req.setNewPassword(newPwd);
        req.setConfirmPassword(confirm);
        return req;
    }

    // ─── changePassword: happy path ───────────────────────────────────────────

    @Test
    void changePassword_succeeds_andInvalidatesTokensAndSessions() {
        when(valueOps.get(anyString())).thenReturn(null); // no rate limit hit
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.matches("current-pass", "hashed-current")).thenReturn(true);
        when(passwordEncoder.matches("new-pass", "hashed-current")).thenReturn(false);
        when(passwordEncoder.encode("new-pass")).thenReturn("hashed-new");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(refreshTokenService).deleteAllByUserId(anyString());
        when(sessionRepository.deleteByUserId(anyString())).thenReturn(0);
        doNothing().when(passwordChangeProducer).publishPasswordChangeEvent(any());
        doNothing().when(notificationProducer).send(any());

        service.changePassword("alice", changeReq("current-pass", "new-pass", "new-pass"));

        verify(refreshTokenService).deleteAllByUserId("user-id-1");
        verify(sessionRepository).deleteByUserId("user-id-1");
        verify(userRepository).save(any());
    }

    @Test
    void changePassword_throwsBadRequest_whenPasswordsDoNotMatch() {
        when(valueOps.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> service.changePassword("alice",
                changeReq("current-pass", "newPass1", "newPass2")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("do not match");
    }

    @Test
    void changePassword_throwsBadRequest_whenCurrentPasswordIncorrect() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.matches("wrong", "hashed-current")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword("alice",
                changeReq("wrong", "newPass1!", "newPass1!")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Current password is incorrect");
    }

    @Test
    void changePassword_throwsBadRequest_whenNewPasswordSameAsCurrent() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.matches("same-pass", "hashed-current")).thenReturn(true); // current matches
        when(passwordEncoder.matches("same-pass", "hashed-current")).thenReturn(true); // new also matches

        assertThatThrownBy(() -> service.changePassword("alice",
                changeReq("same-pass", "same-pass", "same-pass")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must be different");
    }

    @Test
    void changePassword_throwsNotFound_whenUserMissing() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword("ghost",
                changeReq("pass", "newPass1!", "newPass1!")))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── forgotPassword ───────────────────────────────────────────────────────

    @Test
    void forgotPassword_returnsTokenAndMaskedEmail() {
        when(valueOps.get(anyString())).thenReturn(null); // no rate limit
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser()));
        doNothing().when(resetTokenRepository).deleteByEmail(anyString());
        when(resetTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(notificationProducer).send(any());

        var response = service.forgotPassword(forgotReq("alice@example.com"));

        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getEmail()).contains("*");
        assertThat(response.getMessage()).containsIgnoringCase("OTP sent");
    }

    @Test
    void forgotPassword_throwsNotFound_whenEmailUnknown() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.forgotPassword(forgotReq("ghost@example.com")))
                .isInstanceOf(NotFoundException.class);
    }

    private ForgotPasswordRequest forgotReq(String email) {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail(email);
        return req;
    }

    // ─── verifyResetOtp ───────────────────────────────────────────────────────

    @Test
    void verifyResetOtp_returnsSuccessMap_withValidCode() {
        PasswordResetToken token = resetToken("token-xyz", "111222", 0);
        when(resetTokenRepository.findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                anyString(), any(LocalDateTime.class))).thenReturn(Optional.of(token));
        when(resetTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setEmail("alice@example.com");
        req.setOtp("111222");

        Map<String, String> result = service.verifyResetOtp(req);

        assertThat(result.get("message")).containsIgnoringCase("verified");
        assertThat(result.get("resetToken")).isEqualTo("token-xyz");
        assertThat(token.getAttempts()).isEqualTo(0); // reset to 0 after verification
    }

    @Test
    void verifyResetOtp_throwsBadRequest_onWrongCode() {
        PasswordResetToken token = resetToken("token-xyz", "111222", 2);
        when(resetTokenRepository.findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                anyString(), any())).thenReturn(Optional.of(token));
        when(resetTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setEmail("alice@example.com");
        req.setOtp("999999");

        assertThatThrownBy(() -> service.verifyResetOtp(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid OTP");
        assertThat(token.getAttempts()).isEqualTo(3); // incremented
    }

    @Test
    void verifyResetOtp_throwsBadRequest_whenTooManyAttempts() {
        PasswordResetToken token = resetToken("token-xyz", "111222", 5);
        when(resetTokenRepository.findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                anyString(), any())).thenReturn(Optional.of(token));

        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setEmail("alice@example.com");
        req.setOtp("111222");

        assertThatThrownBy(() -> service.verifyResetOtp(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Too many failed attempts");
    }

    @Test
    void verifyResetOtp_throwsBadRequest_whenNoActiveToken() {
        when(resetTokenRepository.findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                anyString(), any())).thenReturn(Optional.empty());

        VerifyOtpRequest req = new VerifyOtpRequest();
        req.setEmail("alice@example.com");
        req.setOtp("111222");

        assertThatThrownBy(() -> service.verifyResetOtp(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("No active reset request");
    }

    // ─── resetPassword ────────────────────────────────────────────────────────

    @Test
    void resetPassword_succeeds_whenOtpVerifiedAndTokenValid() {
        PasswordResetToken token = resetToken("valid-token", "111222", 0);
        when(resetTokenRepository.findByTokenAndUsedFalseAndExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.of(token));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(activeUser()));
        when(passwordEncoder.encode("NewPass@1")).thenReturn("hashed-new");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(resetTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(refreshTokenService).deleteAllByUserId(anyString());
        when(sessionRepository.deleteByUserId(anyString())).thenReturn(0);
        doNothing().when(passwordChangeProducer).publishPasswordChangeEvent(any());
        doNothing().when(notificationProducer).send(any());

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("valid-token");
        req.setNewPassword("NewPass@1");
        req.setConfirmPassword("NewPass@1");

        service.resetPassword(req); // must not throw

        verify(userRepository).save(any());
        verify(resetTokenRepository).save(any()); // marks as used
    }

    @Test
    void resetPassword_throwsBadRequest_whenOtpNotVerified() {
        PasswordResetToken token = resetToken("valid-token", "111222", 2); // attempts != 0
        when(resetTokenRepository.findByTokenAndUsedFalseAndExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.of(token));

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("valid-token");
        req.setNewPassword("NewPass@1");
        req.setConfirmPassword("NewPass@1");

        assertThatThrownBy(() -> service.resetPassword(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("verify OTP first");
    }

    @Test
    void resetPassword_throwsBadRequest_whenPasswordsDoNotMatch() {
        // Exception is thrown before token lookup — no stub needed
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("valid-token");
        req.setNewPassword("NewPass@1");
        req.setConfirmPassword("DifferentPass@2");

        assertThatThrownBy(() -> service.resetPassword(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("do not match");
    }

    @Test
    void resetPassword_throwsBadRequest_whenTokenInvalid() {
        when(resetTokenRepository.findByTokenAndUsedFalseAndExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.empty());

        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("bad-token");
        req.setNewPassword("NewPass@1");
        req.setConfirmPassword("NewPass@1");

        assertThatThrownBy(() -> service.resetPassword(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid or expired reset token");
    }

    // ─── helper ───────────────────────────────────────────────────────────────

    private static PasswordResetToken resetToken(String token, String otp, int attempts) {
        return PasswordResetToken.builder()
                .email("alice@example.com")
                .token(token)
                .otp(otp)
                .attempts(attempts)
                .used(false)
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .expiresAt(LocalDateTime.now().plusMinutes(25))
                .build();
    }
}
