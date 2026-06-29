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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.workfitai.authservice.document.TwoFactorAuth;
import org.workfitai.authservice.dto.request.Disable2FARequest;
import org.workfitai.authservice.dto.request.Enable2FARequest;
import org.workfitai.authservice.dto.response.Enable2FAResponse;
import org.workfitai.authservice.enums.UserStatus;
import org.workfitai.authservice.exception.BadRequestException;
import org.workfitai.authservice.exception.NotFoundException;
import org.workfitai.authservice.messaging.NotificationProducer;
import org.workfitai.authservice.model.User;
import org.workfitai.authservice.repository.TwoFactorAuthRepository;
import org.workfitai.authservice.repository.UserRepository;

import com.warrenstrange.googleauth.GoogleAuthenticator;

@ExtendWith(MockitoExtension.class)
class TwoFactorAuthServiceTest {

    @Mock TwoFactorAuthRepository twoFactorAuthRepository;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock GoogleAuthenticator googleAuthenticator;
    @Mock NotificationProducer notificationProducer;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks TwoFactorAuthService service;

    private User activeUser(String username) {
        return User.builder()
                .id("user-id-1")
                .username(username)
                .email(username + "@example.com")
                .status(UserStatus.ACTIVE)
                .password("hashed-password")
                .build();
    }

    private TwoFactorAuth totpConfig(String userId) {
        return TwoFactorAuth.builder()
                .userId(userId)
                .method("TOTP")
                .secret("TOTPSECRET")
                .enabled(true)
                .enabledAt(LocalDateTime.now())
                .build();
    }

    // ─── enable2FA ────────────────────────────────────────────────────────────

    @Test
    void enable2FA_EMAIL_method_returnsResponse_withNullSecret() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser("alice")));
        when(twoFactorAuthRepository.findByUserId("user-id-1")).thenReturn(Optional.empty());
        when(twoFactorAuthRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-backup");
        doNothing().when(notificationProducer).send(any());

        Enable2FARequest req = new Enable2FARequest();
        req.setMethod("EMAIL");

        Enable2FAResponse response = service.enable2FA("alice", req);

        assertThat(response.getMethod()).isEqualTo("EMAIL");
        assertThat(response.getSecret()).isNull();
        assertThat(response.getQrCodeUrl()).isNull();
        assertThat(response.getBackupCodes()).hasSize(10);
    }

    @Test
    void enable2FA_throwsBadRequest_whenAlreadyEnabled() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser("alice")));
        TwoFactorAuth existing = TwoFactorAuth.builder().userId("user-id-1").enabled(true).build();
        when(twoFactorAuthRepository.findByUserId("user-id-1")).thenReturn(Optional.of(existing));

        Enable2FARequest req = new Enable2FARequest();
        req.setMethod("EMAIL");

        assertThatThrownBy(() -> service.enable2FA("alice", req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already enabled");
    }

    @Test
    void enable2FA_throwsBadRequest_whenInvalidMethod() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser("alice")));
        when(twoFactorAuthRepository.findByUserId("user-id-1")).thenReturn(Optional.empty());

        Enable2FARequest req = new Enable2FARequest();
        req.setMethod("SMS"); // unsupported

        assertThatThrownBy(() -> service.enable2FA("alice", req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid 2FA method");
    }

    @Test
    void enable2FA_throwsNotFound_whenUserDoesNotExist() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        Enable2FARequest req = new Enable2FARequest();
        req.setMethod("EMAIL");

        assertThatThrownBy(() -> service.enable2FA("ghost", req))
                .isInstanceOf(NotFoundException.class);
    }

    // ─── disable2FA ───────────────────────────────────────────────────────────

    @Test
    void disable2FA_succeeds_withCorrectPasswordAndCode() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser("alice")));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
        TwoFactorAuth cfg = totpConfig("user-id-1");
        when(twoFactorAuthRepository.findByUserId("user-id-1")).thenReturn(Optional.of(cfg));
        when(googleAuthenticator.authorize("TOTPSECRET", 123456)).thenReturn(true);
        doNothing().when(notificationProducer).send(any());

        Disable2FARequest req = new Disable2FARequest();
        req.setPassword("password123");
        req.setCode("123456");

        assertThat(service.disable2FA("alice", req)).containsKey("message");
        verify(twoFactorAuthRepository).delete(cfg);
    }

    @Test
    void disable2FA_throwsBadRequest_onWrongPassword() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser("alice")));
        when(passwordEncoder.matches("wrong", "hashed-password")).thenReturn(false);

        Disable2FARequest req = new Disable2FARequest();
        req.setPassword("wrong");
        req.setCode("123456");

        assertThatThrownBy(() -> service.disable2FA("alice", req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid password");
    }

    @Test
    void disable2FA_throwsBadRequest_onWrongTotpCode() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser("alice")));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
        TwoFactorAuth cfg = totpConfig("user-id-1");
        when(twoFactorAuthRepository.findByUserId("user-id-1")).thenReturn(Optional.of(cfg));
        when(googleAuthenticator.authorize("TOTPSECRET", 999999)).thenReturn(false);

        Disable2FARequest req = new Disable2FARequest();
        req.setPassword("password123");
        req.setCode("999999");

        assertThatThrownBy(() -> service.disable2FA("alice", req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid 2FA code");
    }

    // ─── verify2FACode (with TwoFactorAuth object) ────────────────────────────

    @Test
    void verify2FACode_TOTP_returnsTrue_whenAuthorized() {
        TwoFactorAuth cfg = totpConfig("user-id-1");
        when(googleAuthenticator.authorize("TOTPSECRET", 111222)).thenReturn(true);

        assertThat(service.verify2FACode(cfg, "111222")).isTrue();
    }

    @Test
    void verify2FACode_TOTP_returnsFalse_whenNotAuthorized() {
        TwoFactorAuth cfg = totpConfig("user-id-1");
        when(googleAuthenticator.authorize("TOTPSECRET", 999999)).thenReturn(false);

        assertThat(service.verify2FACode(cfg, "999999")).isFalse();
    }

    @Test
    void verify2FACode_EMAIL_alwaysReturnsTrue() {
        TwoFactorAuth emailCfg = TwoFactorAuth.builder()
                .userId("user-id-1").method("EMAIL").enabled(true).build();

        assertThat(service.verify2FACode(emailCfg, "any-code")).isTrue();
    }

    // ─── verifyBackupCode ─────────────────────────────────────────────────────

    @Test
    void verifyBackupCode_returnsTrue_andRemovesUsedCode() {
        List<String> hashedCodes = new ArrayList<>(List.of("hashed-a", "hashed-b"));
        TwoFactorAuth cfg = TwoFactorAuth.builder()
                .userId("user-id-1").method("TOTP").backupCodes(hashedCodes).build();
        when(twoFactorAuthRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.matches("backup-code-a", "hashed-a")).thenReturn(true);

        boolean result = service.verifyBackupCode(cfg, "backup-code-a");

        assertThat(result).isTrue();
        assertThat(cfg.getBackupCodes()).doesNotContain("hashed-a");
    }

    @Test
    void verifyBackupCode_returnsFalse_whenCodeNotFound() {
        List<String> hashedCodes = new ArrayList<>(List.of("hashed-x"));
        TwoFactorAuth cfg = TwoFactorAuth.builder()
                .userId("user-id-1").method("TOTP").backupCodes(hashedCodes).build();
        when(passwordEncoder.matches("wrong-backup", "hashed-x")).thenReturn(false);

        assertThat(service.verifyBackupCode(cfg, "wrong-backup")).isFalse();
    }

    @Test
    void verifyBackupCode_returnsFalse_whenListIsNull() {
        TwoFactorAuth cfg = TwoFactorAuth.builder()
                .userId("user-id-1").method("TOTP").backupCodes(null).build();

        assertThat(service.verifyBackupCode(cfg, "any-code")).isFalse();
    }

    // ─── generateEmailCode / verify2FACode (by userId+method) ─────────────────

    @Test
    void generateEmailCode_storesCodeInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        String code = service.generateEmailCode("user-id-1");

        assertThat(code).matches("\\d{6}");
        verify(valueOps).set(eq("2fa:login:email:user-id-1"), eq(code), eq(5L), eq(TimeUnit.MINUTES));
    }

    @Test
    void verify2FACode_EMAIL_byUserId_returnsTrueAndDeletesKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("2fa:login:email:user-id-1")).thenReturn("555666");

        boolean result = service.verify2FACode("user-id-1", "555666", "EMAIL");

        assertThat(result).isTrue();
        verify(redisTemplate).delete("2fa:login:email:user-id-1");
    }

    @Test
    void verify2FACode_EMAIL_byUserId_returnsFalseOnMismatch() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("2fa:login:email:user-id-1")).thenReturn("555666");

        assertThat(service.verify2FACode("user-id-1", "111111", "EMAIL")).isFalse();
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void verify2FACode_EMAIL_byUserId_returnsFalseWhenExpired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("2fa:login:email:user-id-1")).thenReturn(null);

        assertThat(service.verify2FACode("user-id-1", "555666", "EMAIL")).isFalse();
    }

    @Test
    void verify2FACode_TOTP_byUserId_returnsTrue() {
        TwoFactorAuth cfg = totpConfig("user-id-1");
        when(twoFactorAuthRepository.findByUserId("user-id-1")).thenReturn(Optional.of(cfg));
        when(googleAuthenticator.authorize("TOTPSECRET", 123456)).thenReturn(true);

        assertThat(service.verify2FACode("user-id-1", "123456", "TOTP")).isTrue();
    }

    @Test
    void verify2FACode_TOTP_byUserId_returnsFalseOnNonNumericCode() {
        TwoFactorAuth cfg = totpConfig("user-id-1");
        when(twoFactorAuthRepository.findByUserId("user-id-1")).thenReturn(Optional.of(cfg));

        assertThat(service.verify2FACode("user-id-1", "abc", "TOTP")).isFalse();
    }

    @Test
    void verify2FACode_unknownMethod_throwsBadRequest() {
        assertThatThrownBy(() -> service.verify2FACode("user-id-1", "123456", "CARRIER_PIGEON"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid 2FA method");
    }

    // ─── get2FAStatus ─────────────────────────────────────────────────────────

    @Test
    void get2FAStatus_returnsEnabledTrue_whenConfigured() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser("alice")));
        when(twoFactorAuthRepository.findByUserId("user-id-1"))
                .thenReturn(Optional.of(totpConfig("user-id-1")));

        var status = service.get2FAStatus("alice");

        assertThat(status.get("enabled")).isEqualTo(true);
        assertThat(status.get("method")).isEqualTo("TOTP");
    }

    @Test
    void get2FAStatus_returnsEnabledFalse_whenNotConfigured() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(activeUser("alice")));
        when(twoFactorAuthRepository.findByUserId("user-id-1")).thenReturn(Optional.empty());

        var status = service.get2FAStatus("alice");

        assertThat(status.get("enabled")).isEqualTo(false);
    }
}
