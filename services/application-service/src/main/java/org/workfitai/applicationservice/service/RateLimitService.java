package org.workfitai.applicationservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-process rate limiting service.
 *
 * WARNING (M4): This implementation is NOT distributed-safe. With N service instances
 * the effective limit is maxRequests × N, and state resets on every restart/redeploy.
 * Replace with a Redis-backed solution (e.g. Bucket4j + Redis) for production
 * multi-instance deployments.
 */
@Service
@Slf4j
public class RateLimitService {

    // Runs nightly at 3 AM to evict stale in-memory entries and prevent unbounded growth.
    private static final String CLEANUP_CRON = "0 0 3 * * ?";
    private static final int STALE_ENTRY_CLEANUP_DAYS = 7;

    // Map: operation -> (username -> queue of request timestamps)
    private final Map<String, Map<String, Queue<Instant>>> rateLimits = new ConcurrentHashMap<>();

    /**
     * Check if request is allowed under rate limit
     *
     * @param operation   Operation identifier (e.g., "admin-export")
     * @param username    User making the request
     * @param maxRequests Maximum requests allowed
     * @param windowHours Time window in hours
     * @return true if request is allowed, false if rate limit exceeded
     */
    public boolean isAllowed(String operation, String username, int maxRequests, int windowHours) {
        Map<String, Queue<Instant>> userLimits = rateLimits.computeIfAbsent(
                operation,
                k -> new ConcurrentHashMap<>());

        Queue<Instant> timestamps = userLimits.computeIfAbsent(
                username,
                k -> new ConcurrentLinkedQueue<>());

        Instant now = Instant.now();
        Instant windowStart = now.minus(windowHours, ChronoUnit.HOURS);

        // Remove timestamps outside the window
        timestamps.removeIf(timestamp -> timestamp.isBefore(windowStart));

        // Check if limit exceeded
        if (timestamps.size() >= maxRequests) {
            log.warn("Rate limit exceeded for operation={}, user={}, count={}/{} in {}h",
                    operation, username, timestamps.size(), maxRequests, windowHours);
            return false;
        }

        // Record this request
        timestamps.add(now);
        log.debug("Rate limit check passed for operation={}, user={}, count={}/{}",
                operation, username, timestamps.size(), maxRequests);
        return true;
    }

    /**
     * Get remaining requests for a user
     */
    public int getRemainingRequests(String operation, String username, int maxRequests, int windowHours) {
        Map<String, Queue<Instant>> userLimits = rateLimits.get(operation);
        if (userLimits == null) {
            return maxRequests;
        }

        Queue<Instant> timestamps = userLimits.get(username);
        if (timestamps == null) {
            return maxRequests;
        }

        Instant now = Instant.now();
        Instant windowStart = now.minus(windowHours, ChronoUnit.HOURS);

        // Count valid timestamps in window
        long validCount = timestamps.stream()
                .filter(timestamp -> timestamp.isAfter(windowStart))
                .count();

        return Math.max(0, maxRequests - (int) validCount);
    }

    /**
     * Get time until rate limit resets (in seconds)
     */
    public long getResetTimeSeconds(String operation, String username, int windowHours) {
        Map<String, Queue<Instant>> userLimits = rateLimits.get(operation);
        if (userLimits == null) {
            return 0;
        }

        Queue<Instant> timestamps = userLimits.get(username);
        if (timestamps == null || timestamps.isEmpty()) {
            return 0;
        }

        // Oldest timestamp in queue
        Instant oldest = timestamps.peek();
        if (oldest == null) {
            return 0;
        }

        Instant resetTime = oldest.plus(windowHours, ChronoUnit.HOURS);
        Instant now = Instant.now();

        if (resetTime.isBefore(now)) {
            return 0;
        }

        return ChronoUnit.SECONDS.between(now, resetTime);
    }

    /**
     * Clear rate limits for a specific operation (testing/admin use)
     */
    public void clearRateLimits(String operation) {
        rateLimits.remove(operation);
        log.info("Cleared rate limits for operation={}", operation);
    }

    /**
     * Scheduled cleanup of expired entries (runs daily at 3 AM)
     * Prevents memory leaks from stale data
     */
    @Scheduled(cron = CLEANUP_CRON)
    public void cleanupExpiredEntries() {
        log.info("Starting scheduled cleanup of expired rate limit entries...");

        AtomicInteger totalRemoved = new AtomicInteger(0);

        for (Map.Entry<String, Map<String, Queue<Instant>>> operationEntry : rateLimits.entrySet()) {
            String operation = operationEntry.getKey();
            Map<String, Queue<Instant>> userLimits = operationEntry.getValue();

            Instant staleThreshold = Instant.now().minus(STALE_ENTRY_CLEANUP_DAYS, ChronoUnit.DAYS);

            // Remove users with no recent activity
            userLimits.entrySet().removeIf(entry -> {
                Queue<Instant> timestamps = entry.getValue();
                timestamps.removeIf(timestamp -> timestamp.isBefore(staleThreshold));

                if (timestamps.isEmpty()) {
                    totalRemoved.incrementAndGet();
                    return true;
                }
                return false;
            });
        }

        log.info("Completed rate limit cleanup, removed {} stale user entries", totalRemoved.get());
    }
}
