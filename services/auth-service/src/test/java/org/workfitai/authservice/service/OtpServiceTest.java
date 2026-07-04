package org.workfitai.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.workfitai.authservice.dto.request.PendingRegistration;
import org.workfitai.authservice.enums.UserRole;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    private OtpService otpService;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(redis.opsForValue()).thenReturn(valueOps);
        otpService = new OtpService(redis, objectMapper, 86400L, false);
    }

    // ─── generateOtp ──────────────────────────────────────────────────────────

    @Test
    void generateOtp_returns6DigitString() {
        String otp = otpService.generateOtp();

        assertThat(otp).matches("\\d{6}");
    }

    @RepeatedTest(5)
    void generateOtp_producesValueInRange() {
        int code = Integer.parseInt(otpService.generateOtp());
        assertThat(code).isBetween(100000, 999999);
    }

    @Test
    void generateOtp_devFixedMode_alwaysReturns000000() {
        OtpService devService = new OtpService(redis, objectMapper, 86400L, true);
        assertThat(devService.generateOtp()).isEqualTo("000000");
    }

    // ─── saveOtp / verifyOtp ──────────────────────────────────────────────────

    @Test
    void verifyOtp_returnsTrue_whenCodeMatches() {
        when(valueOps.get("auth:otp:user@example.com")).thenReturn("123456");

        boolean result = otpService.verifyOtp("user@example.com", "123456");

        assertThat(result).isTrue();
        verify(redis).delete("auth:otp:user@example.com");
    }

    @Test
    void verifyOtp_returnsFalse_whenCodeDoesNotMatch() {
        when(valueOps.get("auth:otp:user@example.com")).thenReturn("123456");

        assertThat(otpService.verifyOtp("user@example.com", "999999")).isFalse();
    }

    @Test
    void verifyOtp_returnsFalse_whenKeyNotFound() {
        when(valueOps.get("auth:otp:unknown@example.com")).thenReturn(null);

        assertThat(otpService.verifyOtp("unknown@example.com", "123456")).isFalse();
    }

    @Test
    void verifyOtp_emailLowercased_beforeLookup() {
        when(valueOps.get("auth:otp:user@example.com")).thenReturn("111111");

        boolean result = otpService.verifyOtp("USER@EXAMPLE.COM", "111111");

        assertThat(result).isTrue();
        verify(redis).delete("auth:otp:user@example.com");
    }

    @Test
    void saveOtp_stores_withExpiry() {
        otpService.saveOtp("user@example.com", "654321");

        verify(valueOps).set(eq("auth:otp:user@example.com"), eq("654321"), any(Duration.class));
    }

    // ─── savePendingRegistration / getPendingRegistration ─────────────────────

    @Test
    void savePendingRegistration_serializesAndStores() throws Exception {
        PendingRegistration pending = PendingRegistration.builder()
                .email("user@example.com")
                .username("user")
                .fullName("John Doe")
                .role(UserRole.CANDIDATE)
                .build();

        otpService.savePendingRegistration("user@example.com", pending);

        verify(valueOps).set(eq("auth:pending:user@example.com"), anyString(), any(Duration.class));
    }

    @Test
    void getPendingRegistration_deserializesFromRedis() throws Exception {
        PendingRegistration pending = PendingRegistration.builder()
                .email("user@example.com")
                .username("user")
                .fullName("John Doe")
                .role(UserRole.CANDIDATE)
                .build();

        String serialized = objectMapper.writeValueAsString(pending);
        when(valueOps.get("auth:pending:user@example.com")).thenReturn(serialized);

        PendingRegistration result = otpService.getPendingRegistration("user@example.com");

        assertThat(result).isNotNull();
        assertThat(result.getEmail()).isEqualTo("user@example.com");
        assertThat(result.getUsername()).isEqualTo("user");
    }

    @Test
    void getPendingRegistration_returnsNull_whenKeyNotFound() {
        when(valueOps.get("auth:pending:missing@example.com")).thenReturn(null);

        assertThat(otpService.getPendingRegistration("missing@example.com")).isNull();
    }

    @Test
    void getPendingRegistration_returnsNull_whenDeserializationFails() {
        when(valueOps.get("auth:pending:bad@example.com")).thenReturn("not-valid-json");

        assertThat(otpService.getPendingRegistration("bad@example.com")).isNull();
    }

    @Test
    void deletePendingRegistration_deletesKey() {
        otpService.deletePendingRegistration("user@example.com");

        verify(redis).delete("auth:pending:user@example.com");
    }

    // ─── saveOtp with payload ─────────────────────────────────────────────────

    @Test
    void saveOtpWithPayload_storesSerializedRecord() {
        PendingRegistration payload = PendingRegistration.builder()
                .email("user@example.com").role(UserRole.CANDIDATE).build();

        otpService.saveOtp("user@example.com", "123456", payload);

        verify(valueOps).set(eq("auth:otp:user@example.com"), anyString(), any(Duration.class));
    }

    @Test
    void getOtp_returnsNullWhenKeyMissing() {
        when(valueOps.get("auth:otp:missing@example.com")).thenReturn(null);

        OtpService.OtpPayload<PendingRegistration> result =
                otpService.getOtp("missing@example.com", PendingRegistration.class);

        assertThat(result).isNull();
    }

    @Test
    void deleteOtp_removesCorrectKey() {
        otpService.deleteOtp("user@example.com");

        verify(redis).delete("auth:otp:user@example.com");
    }
}
