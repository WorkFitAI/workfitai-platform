package org.workfitai.applicationservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimitService
 */
class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
    }

    @Test
    void testIsAllowed_FirstRequest_ShouldBeAllowed() {
        // Arrange
        String operation = "test-operation";
        String username = "testuser";
        int maxRequests = 5;
        int windowHours = 24;

        // Act
        boolean allowed = rateLimitService.isAllowed(operation, username, maxRequests, windowHours);

        // Assert
        assertTrue(allowed, "First request should be allowed");
    }

    @Test
    void testIsAllowed_WithinLimit_ShouldBeAllowed() {
        // Arrange
        String operation = "test-operation";
        String username = "testuser";
        int maxRequests = 5;
        int windowHours = 24;

        // Act - Make 4 requests (within limit)
        for (int i = 0; i < 4; i++) {
            boolean allowed = rateLimitService.isAllowed(operation, username, maxRequests, windowHours);
            assertTrue(allowed, "Request " + (i + 1) + " should be allowed");
        }

        // Fifth request should also be allowed
        boolean fifthAllowed = rateLimitService.isAllowed(operation, username, maxRequests, windowHours);
        assertTrue(fifthAllowed, "Fifth request should be allowed (exactly at limit)");
    }

    @Test
    void testIsAllowed_ExceedLimit_ShouldBeDenied() {
        // Arrange
        String operation = "admin-export";
        String username = "admin1";
        int maxRequests = 5;
        int windowHours = 24;

        // Act - Make 5 requests (fill up the limit)
        for (int i = 0; i < 5; i++) {
            rateLimitService.isAllowed(operation, username, maxRequests, windowHours);
        }

        // Sixth request should be denied
        boolean sixthAllowed = rateLimitService.isAllowed(operation, username, maxRequests, windowHours);

        // Assert
        assertFalse(sixthAllowed, "Sixth request should be denied (exceeded limit)");
    }

    @Test
    void testGetRemainingRequests_NoRequests_ShouldReturnMaxRequests() {
        // Arrange
        String operation = "test-operation";
        String username = "testuser";
        int maxRequests = 5;
        int windowHours = 24;

        // Act
        int remaining = rateLimitService.getRemainingRequests(operation, username, maxRequests, windowHours);

        // Assert
        assertEquals(maxRequests, remaining, "Should have all requests remaining");
    }

    @Test
    void testGetRemainingRequests_AfterSomeRequests_ShouldReturnCorrectCount() {
        // Arrange
        String operation = "test-operation";
        String username = "testuser";
        int maxRequests = 5;
        int windowHours = 24;

        // Make 3 requests
        for (int i = 0; i < 3; i++) {
            rateLimitService.isAllowed(operation, username, maxRequests, windowHours);
        }

        // Act
        int remaining = rateLimitService.getRemainingRequests(operation, username, maxRequests, windowHours);

        // Assert
        assertEquals(2, remaining, "Should have 2 requests remaining (5 - 3)");
    }

    @Test
    void testGetRemainingRequests_AfterExceedingLimit_ShouldReturnZero() {
        // Arrange
        String operation = "test-operation";
        String username = "testuser";
        int maxRequests = 5;
        int windowHours = 24;

        // Make 6 requests (exceed limit)
        for (int i = 0; i < 6; i++) {
            rateLimitService.isAllowed(operation, username, maxRequests, windowHours);
        }

        // Act
        int remaining = rateLimitService.getRemainingRequests(operation, username, maxRequests, windowHours);

        // Assert
        assertEquals(0, remaining, "Should have 0 requests remaining");
    }

    @Test
    void testDifferentOperations_ShouldHaveSeparateLimits() {
        // Arrange
        String operation1 = "admin-export";
        String operation2 = "admin-delete";
        String username = "admin1";
        int maxRequests = 5;
        int windowHours = 24;

        // Act - Fill operation1 limit
        for (int i = 0; i < 5; i++) {
            rateLimitService.isAllowed(operation1, username, maxRequests, windowHours);
        }

        // Operation1 should be denied
        boolean op1Allowed = rateLimitService.isAllowed(operation1, username, maxRequests, windowHours);

        // Operation2 should still be allowed
        boolean op2Allowed = rateLimitService.isAllowed(operation2, username, maxRequests, windowHours);

        // Assert
        assertFalse(op1Allowed, "Operation1 should be denied");
        assertTrue(op2Allowed, "Operation2 should be allowed (separate limit)");
    }

    @Test
    void testDifferentUsers_ShouldHaveSeparateLimits() {
        // Arrange
        String operation = "admin-export";
        String user1 = "admin1";
        String user2 = "admin2";
        int maxRequests = 5;
        int windowHours = 24;

        // Act - Fill user1 limit
        for (int i = 0; i < 5; i++) {
            rateLimitService.isAllowed(operation, user1, maxRequests, windowHours);
        }

        // User1 should be denied
        boolean user1Allowed = rateLimitService.isAllowed(operation, user1, maxRequests, windowHours);

        // User2 should still be allowed
        boolean user2Allowed = rateLimitService.isAllowed(operation, user2, maxRequests, windowHours);

        // Assert
        assertFalse(user1Allowed, "User1 should be denied");
        assertTrue(user2Allowed, "User2 should be allowed (separate limit)");
    }

    @Test
    void testClearRateLimits_ShouldResetLimits() {
        // Arrange
        String operation = "test-operation";
        String username = "testuser";
        int maxRequests = 5;
        int windowHours = 24;

        // Fill the limit
        for (int i = 0; i < 5; i++) {
            rateLimitService.isAllowed(operation, username, maxRequests, windowHours);
        }

        // Verify limit is reached
        assertFalse(rateLimitService.isAllowed(operation, username, maxRequests, windowHours));

        // Act - Clear limits
        rateLimitService.clearRateLimits(operation);

        // Assert - Should be allowed again
        boolean allowedAfterClear = rateLimitService.isAllowed(operation, username, maxRequests, windowHours);
        assertTrue(allowedAfterClear, "Should be allowed after clearing rate limits");
    }

    @Test
    void testGetResetTimeSeconds_NoRequests_ShouldReturnZero() {
        // Arrange
        String operation = "test-operation";
        String username = "testuser";
        int windowHours = 24;

        // Act
        long resetTime = rateLimitService.getResetTimeSeconds(operation, username, windowHours);

        // Assert
        assertEquals(0, resetTime, "Reset time should be 0 when no requests made");
    }

    @Test
    void testGetResetTimeSeconds_AfterRequest_ShouldReturnPositiveValue() {
        // Arrange
        String operation = "test-operation";
        String username = "testuser";
        int maxRequests = 5;
        int windowHours = 24;

        // Make a request
        rateLimitService.isAllowed(operation, username, maxRequests, windowHours);

        // Act
        long resetTime = rateLimitService.getResetTimeSeconds(operation, username, windowHours);

        // Assert
        assertTrue(resetTime > 0, "Reset time should be positive after making a request");
        assertTrue(resetTime <= 24 * 3600, "Reset time should be within 24 hours");
    }

    @Test
    void testCleanupExpiredEntries_ShouldNotAffectFunctionality() {
        // Arrange
        String operation = "test-operation";
        String username = "testuser";
        int maxRequests = 5;
        int windowHours = 24;

        // Make some requests
        for (int i = 0; i < 3; i++) {
            rateLimitService.isAllowed(operation, username, maxRequests, windowHours);
        }

        // Act - Run cleanup (shouldn't remove recent entries)
        rateLimitService.cleanupExpiredEntries();

        // Assert - Remaining requests should still be correct
        int remaining = rateLimitService.getRemainingRequests(operation, username, maxRequests, windowHours);
        assertEquals(2, remaining, "Cleanup should not affect recent entries");
    }
}
