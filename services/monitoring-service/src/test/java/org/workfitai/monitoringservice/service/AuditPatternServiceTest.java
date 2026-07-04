package org.workfitai.monitoringservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.workfitai.monitoringservice.dto.AuditPatternDto;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditPatternServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private AuditPatternService auditPatternService;

    @BeforeEach
    void setUp() {
        auditPatternService = new AuditPatternService(redisTemplate);
    }

    private void stubHashOps() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void resolveMessage_returnsCustomValue_whenRedisHasOverride() {
        stubHashOps();
        when(hashOperations.get(AuditPatternService.REDIS_HASH_KEY, "CREATE")).thenReturn("Custom created message");

        Optional<String> result = auditPatternService.resolveMessage("CREATE");

        assertThat(result).contains("Custom created message");
    }

    @Test
    void resolveMessage_fallsBackToDefault_whenRedisHasNoOverride() {
        stubHashOps();
        when(hashOperations.get(eq(AuditPatternService.REDIS_HASH_KEY), any())).thenReturn(null);

        Optional<String> result = auditPatternService.resolveMessage("CREATE");

        assertThat(result).isEqualTo(Optional.ofNullable(AuditPatternService.DEFAULT_PATTERNS.get("CREATE")));
    }

    @Test
    void resolveMessage_fallsBackToDefault_whenRedisThrows() {
        stubHashOps();
        when(hashOperations.get(eq(AuditPatternService.REDIS_HASH_KEY), any())).thenThrow(new RuntimeException("redis down"));

        Optional<String> result = auditPatternService.resolveMessage("CREATE");

        assertThat(result).isEqualTo(Optional.ofNullable(AuditPatternService.DEFAULT_PATTERNS.get("CREATE")));
    }

    @Test
    void resolveMessage_returnsEmpty_whenKeyUnknownAndNoOverride() {
        stubHashOps();
        when(hashOperations.get(eq(AuditPatternService.REDIS_HASH_KEY), any())).thenReturn(null);

        Optional<String> result = auditPatternService.resolveMessage("__totally_unknown_key__");

        assertThat(result).isEmpty();
    }

    @Test
    void getAllPatterns_mergesDefaultsWithCustomOverrides() {
        stubHashOps();
        String firstKey = AuditPatternService.DEFAULT_PATTERNS.keySet().iterator().next();
        when(hashOperations.entries(AuditPatternService.REDIS_HASH_KEY))
                .thenReturn(Map.of(firstKey, "Overridden message"));

        var patterns = auditPatternService.getAllPatterns();

        assertThat(patterns).hasSize(AuditPatternService.DEFAULT_PATTERNS.size());
        AuditPatternDto overridden = patterns.stream()
                .filter(p -> p.key().equals(firstKey))
                .findFirst()
                .orElseThrow();
        assertThat(overridden.customized()).isTrue();
        assertThat(overridden.message()).isEqualTo("Overridden message");
    }

    @Test
    void getAllPatterns_returnsDefaultsOnly_whenRedisUnavailable() {
        stubHashOps();
        when(hashOperations.entries(AuditPatternService.REDIS_HASH_KEY)).thenThrow(new RuntimeException("redis down"));

        var patterns = auditPatternService.getAllPatterns();

        assertThat(patterns).hasSize(AuditPatternService.DEFAULT_PATTERNS.size());
        assertThat(patterns).allMatch(p -> !p.customized());
    }

    @Test
    void getPattern_returnsEmpty_whenKeyUnknown() {
        Optional<AuditPatternDto> result = auditPatternService.getPattern("__unknown__");

        assertThat(result).isEmpty();
    }

    @Test
    void getPattern_returnsCustomized_whenRedisHasOverride() {
        stubHashOps();
        String key = AuditPatternService.DEFAULT_PATTERNS.keySet().iterator().next();
        when(hashOperations.get(AuditPatternService.REDIS_HASH_KEY, key)).thenReturn("Custom");

        Optional<AuditPatternDto> result = auditPatternService.getPattern(key);

        assertThat(result).isPresent();
        assertThat(result.get().customized()).isTrue();
        assertThat(result.get().message()).isEqualTo("Custom");
    }

    @Test
    void getPattern_returnsDefault_whenRedisLookupFails() {
        stubHashOps();
        String key = AuditPatternService.DEFAULT_PATTERNS.keySet().iterator().next();
        when(hashOperations.get(AuditPatternService.REDIS_HASH_KEY, key)).thenThrow(new RuntimeException("redis down"));

        Optional<AuditPatternDto> result = auditPatternService.getPattern(key);

        assertThat(result).isPresent();
        assertThat(result.get().customized()).isFalse();
    }

    @Test
    void updatePattern_throws_whenKeyUnknown() {
        assertThatThrownBy(() -> auditPatternService.updatePattern("__unknown__", "msg"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updatePattern_throws_whenMessageBlank() {
        String key = AuditPatternService.DEFAULT_PATTERNS.keySet().iterator().next();

        assertThatThrownBy(() -> auditPatternService.updatePattern(key, "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updatePattern_savesTrimmedMessage_whenValid() {
        stubHashOps();
        String key = AuditPatternService.DEFAULT_PATTERNS.keySet().iterator().next();

        AuditPatternDto result = auditPatternService.updatePattern(key, "  trimmed message  ");

        assertThat(result.message()).isEqualTo("trimmed message");
        assertThat(result.customized()).isTrue();
        verify(hashOperations).put(AuditPatternService.REDIS_HASH_KEY, key, "trimmed message");
    }

    @Test
    void resetPattern_throws_whenKeyUnknown() {
        assertThatThrownBy(() -> auditPatternService.resetPattern("__unknown__"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resetPattern_deletesOverride_andReturnsDefault() {
        stubHashOps();
        String key = AuditPatternService.DEFAULT_PATTERNS.keySet().iterator().next();

        AuditPatternDto result = auditPatternService.resetPattern(key);

        assertThat(result.customized()).isFalse();
        assertThat(result.message()).isEqualTo(AuditPatternService.DEFAULT_PATTERNS.get(key));
        verify(hashOperations).delete(AuditPatternService.REDIS_HASH_KEY, key);
    }

    @Test
    void resetAll_deletesEntireHashKey() {
        auditPatternService.resetAll();

        verify(redisTemplate).delete(AuditPatternService.REDIS_HASH_KEY);
    }
}
