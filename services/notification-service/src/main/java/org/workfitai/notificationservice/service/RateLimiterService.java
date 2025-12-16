package org.workfitai.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Token Bucket Rate Limiter for notifications.
 * Uses Redis for distributed rate limiting across multiple instances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Sliding Window Rate Limiter.
     * 
     * @param key           Unique key (e.g., "notif:user@example.com")
     * @param maxRequests   Maximum requests allowed in the window
     * @param windowSeconds Time window in seconds
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean allowRequest(String key, int maxRequests, int windowSeconds) {
        try {
            String redisKey = "rate_limit:" + key;
            Long currentCount = redisTemplate.opsForValue().increment(redisKey);

            if (currentCount == null) {
                return true; // Redis error, allow request
            }

            if (currentCount == 1) {
                // First request, set expiration
                redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
            }

            boolean allowed = currentCount <= maxRequests;

            if (!allowed) {
                log.warn("Rate limit exceeded: key={}, count={}/{}, window={}s",
                        key, currentCount, maxRequests, windowSeconds);
            }

            return allowed;

        } catch (Exception e) {
            log.error("Rate limiter error for key {}: {}", key, e.getMessage());
            return true; // Fail open: allow request on error
        }
    }

    /**
     * Token Bucket Rate Limiter (more flexible).
     * Allows burst traffic while maintaining average rate.
     * 
     * @param key            Unique key
     * @param bucketCapacity Maximum tokens in bucket
     * @param refillRate     Tokens added per second
     * @return true if request is allowed
     */
    public boolean allowRequestWithBucket(String key, int bucketCapacity, double refillRate) {
        try {
            String tokensKey = "bucket:tokens:" + key;
            String timestampKey = "bucket:timestamp:" + key;

            long now = System.currentTimeMillis() / 1000; // Current time in seconds

            // Get current state
            String tokensStr = redisTemplate.opsForValue().get(tokensKey);
            String timestampStr = redisTemplate.opsForValue().get(timestampKey);

            double tokens = tokensStr != null ? Double.parseDouble(tokensStr) : bucketCapacity;
            long lastUpdate = timestampStr != null ? Long.parseLong(timestampStr) : now;

            // Calculate tokens to add based on elapsed time
            long elapsed = now - lastUpdate;
            double tokensToAdd = elapsed * refillRate;
            tokens = Math.min(bucketCapacity, tokens + tokensToAdd);

            boolean allowed = tokens >= 1.0;

            if (allowed) {
                // Consume 1 token
                tokens -= 1.0;
            }

            // Save updated state
            redisTemplate.opsForValue().set(tokensKey, String.valueOf(tokens), Duration.ofHours(1));
            redisTemplate.opsForValue().set(timestampKey, String.valueOf(now), Duration.ofHours(1));

            if (!allowed) {
                log.warn("Token bucket exhausted: key={}, tokens={:.2f}/{}", key, tokens, bucketCapacity);
            }

            return allowed;

        } catch (Exception e) {
            log.error("Token bucket error for key {}: {}", key, e.getMessage());
            return true; // Fail open
        }
    }

    /**
     * Check remaining quota without consuming.
     */
    public int getRemainingQuota(String key, int maxRequests) {
        try {
            String redisKey = "rate_limit:" + key;
            String countStr = redisTemplate.opsForValue().get(redisKey);

            if (countStr == null) {
                return maxRequests;
            }

            long currentCount = Long.parseLong(countStr);
            return Math.max(0, maxRequests - (int) currentCount);

        } catch (Exception e) {
            log.error("Error getting remaining quota: {}", e.getMessage());
            return maxRequests;
        }
    }

    /**
     * Reset rate limit for a specific key.
     */
    public void resetRateLimit(String key) {
        try {
            redisTemplate.delete("rate_limit:" + key);
            log.info("Rate limit reset for key: {}", key);
        } catch (Exception e) {
            log.error("Error resetting rate limit: {}", e.getMessage());
        }
    }
}
