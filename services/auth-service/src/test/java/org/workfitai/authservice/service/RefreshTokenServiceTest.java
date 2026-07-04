package org.workfitai.authservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        Mockito.lenient().when(redis.opsForValue()).thenReturn(valueOps);
        service = new RefreshTokenService(redis, 604_800_000L); // 7 days
    }

    // ─── saveJti ──────────────────────────────────────────────────────────────

    @Test
    void saveJti_storesWithTtl() {
        service.saveJti("user-1", "desktop", "jti-abc");

        verify(valueOps).set(
                eq("auth:rt:user-1:desktop"),
                eq("jti-abc"),
                any(Duration.class));
    }

    @Test
    void saveJti_overwritesPreviousValue() {
        service.saveJti("user-1", "desktop", "jti-old");
        service.saveJti("user-1", "desktop", "jti-new");

        verify(valueOps).set(eq("auth:rt:user-1:desktop"), eq("jti-old"), any(Duration.class));
        verify(valueOps).set(eq("auth:rt:user-1:desktop"), eq("jti-new"), any(Duration.class));
    }

    // ─── getJti ───────────────────────────────────────────────────────────────

    @Test
    void getJti_returnsStoredValue() {
        when(valueOps.get("auth:rt:user-1:mobile")).thenReturn("jti-xyz");

        assertThat(service.getJti("user-1", "mobile")).isEqualTo("jti-xyz");
    }

    @Test
    void getJti_returnsNull_whenKeyAbsent() {
        when(valueOps.get("auth:rt:user-1:unknown-device")).thenReturn(null);

        assertThat(service.getJti("user-1", "unknown-device")).isNull();
    }

    // ─── delete ───────────────────────────────────────────────────────────────

    @Test
    void delete_removesDeviceScopedKey() {
        service.delete("user-1", "tablet");

        verify(redis).delete("auth:rt:user-1:tablet");
    }

    // ─── deleteAllByUserId ────────────────────────────────────────────────────

    @Test
    void deleteAllByUserId_deletesAllMatchingKeys() {
        when(redis.keys("auth:rt:user-1:*"))
                .thenReturn(Set.of("auth:rt:user-1:desktop", "auth:rt:user-1:mobile"));

        service.deleteAllByUserId("user-1");

        verify(redis).delete("auth:rt:user-1:desktop");
        verify(redis).delete("auth:rt:user-1:mobile");
    }

    @Test
    void deleteAllByUserId_noOp_whenNoKeysFound() {
        when(redis.keys("auth:rt:user-2:*")).thenReturn(Set.of());

        service.deleteAllByUserId("user-2"); // must not throw
    }
}
