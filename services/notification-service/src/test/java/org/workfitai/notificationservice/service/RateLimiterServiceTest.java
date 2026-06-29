package org.workfitai.notificationservice.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimiterService rateLimiterService;

    private void init() {
        rateLimiterService = new RateLimiterService(redisTemplate);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void allowRequest_allowsFirstRequest_andSetsExpiration() {
        init();
        when(valueOperations.increment("rate_limit:user1")).thenReturn(1L);

        boolean allowed = rateLimiterService.allowRequest("user1", 10, 3600);

        assertThat(allowed).isTrue();
        verify(redisTemplate).expire("rate_limit:user1", Duration.ofSeconds(3600));
    }

    @Test
    void allowRequest_allowsSubsequentRequestUnderLimit_withoutResettingExpiration() {
        init();
        when(valueOperations.increment("rate_limit:user1")).thenReturn(5L);

        boolean allowed = rateLimiterService.allowRequest("user1", 10, 3600);

        assertThat(allowed).isTrue();
        verify(redisTemplate, never()).expire(any(), any());
    }

    @Test
    void allowRequest_blocksRequest_whenOverLimit() {
        init();
        when(valueOperations.increment("rate_limit:user1")).thenReturn(11L);

        boolean allowed = rateLimiterService.allowRequest("user1", 10, 3600);

        assertThat(allowed).isFalse();
    }

    @Test
    void allowRequest_allowsRequest_whenRedisReturnsNull() {
        init();
        when(valueOperations.increment("rate_limit:user1")).thenReturn(null);

        boolean allowed = rateLimiterService.allowRequest("user1", 10, 3600);

        assertThat(allowed).isTrue();
    }

    @Test
    void allowRequest_failsOpen_whenRedisThrows() {
        init();
        when(valueOperations.increment("rate_limit:user1")).thenThrow(new RuntimeException("redis down"));

        boolean allowed = rateLimiterService.allowRequest("user1", 10, 3600);

        assertThat(allowed).isTrue();
    }

    @Test
    void allowRequestWithBucket_allowsAndConsumesToken_whenBucketFull() {
        init();
        when(valueOperations.get("bucket:tokens:user1")).thenReturn(null);
        when(valueOperations.get("bucket:timestamp:user1")).thenReturn(null);

        boolean allowed = rateLimiterService.allowRequestWithBucket("user1", 5, 1.0);

        assertThat(allowed).isTrue();
        verify(valueOperations).set(eq("bucket:tokens:user1"), eq("4.0"), any(Duration.class));
    }

    @Test
    void allowRequestWithBucket_blocksRequest_whenBucketExhausted() {
        init();
        when(valueOperations.get("bucket:tokens:user1")).thenReturn("0.0");
        when(valueOperations.get("bucket:timestamp:user1"))
                .thenReturn(String.valueOf(System.currentTimeMillis() / 1000));

        boolean allowed = rateLimiterService.allowRequestWithBucket("user1", 5, 0.0);

        assertThat(allowed).isFalse();
    }

    @Test
    void allowRequestWithBucket_failsOpen_whenRedisThrows() {
        init();
        when(valueOperations.get("bucket:tokens:user1")).thenThrow(new RuntimeException("redis down"));

        boolean allowed = rateLimiterService.allowRequestWithBucket("user1", 5, 1.0);

        assertThat(allowed).isTrue();
    }

    @Test
    void getRemainingQuota_returnsMax_whenNoCountStored() {
        init();
        when(valueOperations.get("rate_limit:user1")).thenReturn(null);

        int remaining = rateLimiterService.getRemainingQuota("user1", 10);

        assertThat(remaining).isEqualTo(10);
    }

    @Test
    void getRemainingQuota_returnsRemaining_whenCountStored() {
        init();
        when(valueOperations.get("rate_limit:user1")).thenReturn("4");

        int remaining = rateLimiterService.getRemainingQuota("user1", 10);

        assertThat(remaining).isEqualTo(6);
    }

    @Test
    void getRemainingQuota_neverNegative_whenCountExceedsMax() {
        init();
        when(valueOperations.get("rate_limit:user1")).thenReturn("15");

        int remaining = rateLimiterService.getRemainingQuota("user1", 10);

        assertThat(remaining).isZero();
    }

    @Test
    void getRemainingQuota_returnsMax_whenRedisThrows() {
        init();
        when(valueOperations.get("rate_limit:user1")).thenThrow(new RuntimeException("redis down"));

        int remaining = rateLimiterService.getRemainingQuota("user1", 10);

        assertThat(remaining).isEqualTo(10);
    }

    @Test
    void resetRateLimit_deletesKey() {
        init();

        rateLimiterService.resetRateLimit("user1");

        verify(redisTemplate).delete("rate_limit:user1");
    }

    @Test
    void resetRateLimit_swallowsException() {
        rateLimiterService = new RateLimiterService(redisTemplate);
        when(redisTemplate.delete("rate_limit:user1")).thenThrow(new RuntimeException("redis down"));

        rateLimiterService.resetRateLimit("user1");
        // no exception propagated
    }
}
