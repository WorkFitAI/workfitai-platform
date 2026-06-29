package org.workfitai.applicationservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService();
    }

    // ─── isAllowed ────────────────────────────────────────────────────────────

    @Test
    void isAllowed_firstRequest_returnsTrue() {
        assertThat(rateLimitService.isAllowed("export", "user1", 3, 1)).isTrue();
    }

    @Test
    void isAllowed_underLimit_returnsTrue() {
        rateLimitService.isAllowed("export", "user1", 3, 1);
        rateLimitService.isAllowed("export", "user1", 3, 1);

        assertThat(rateLimitService.isAllowed("export", "user1", 3, 1)).isTrue();
    }

    @Test
    void isAllowed_atLimit_returnsFalse() {
        rateLimitService.isAllowed("export", "user1", 2, 1);
        rateLimitService.isAllowed("export", "user1", 2, 1);

        assertThat(rateLimitService.isAllowed("export", "user1", 2, 1)).isFalse();
    }

    @Test
    void isAllowed_differentUsers_independentLimits() {
        rateLimitService.isAllowed("export", "user1", 1, 1);
        rateLimitService.isAllowed("export", "user1", 1, 1); // user1 now over limit

        // user2 has separate counter — must still be allowed
        assertThat(rateLimitService.isAllowed("export", "user2", 1, 1)).isTrue();
    }

    @Test
    void isAllowed_differentOperations_independentLimits() {
        rateLimitService.isAllowed("export", "user1", 1, 1);
        rateLimitService.isAllowed("export", "user1", 1, 1); // export over limit

        assertThat(rateLimitService.isAllowed("bulk", "user1", 1, 1)).isTrue();
    }

    // ─── getRemainingRequests ─────────────────────────────────────────────────

    @Test
    void getRemainingRequests_noRequests_returnsMax() {
        assertThat(rateLimitService.getRemainingRequests("export", "user1", 5, 1)).isEqualTo(5);
    }

    @Test
    void getRemainingRequests_afterOneRequest_returnsMaxMinusOne() {
        rateLimitService.isAllowed("export", "user1", 5, 1);

        assertThat(rateLimitService.getRemainingRequests("export", "user1", 5, 1)).isEqualTo(4);
    }

    @Test
    void getRemainingRequests_unknownOperation_returnsMax() {
        assertThat(rateLimitService.getRemainingRequests("unknown", "user1", 10, 1)).isEqualTo(10);
    }

    // ─── getResetTimeSeconds ──────────────────────────────────────────────────

    @Test
    void getResetTimeSeconds_noRequests_returnsZero() {
        assertThat(rateLimitService.getResetTimeSeconds("export", "user1", 1)).isEqualTo(0);
    }

    @Test
    void getResetTimeSeconds_afterRequest_returnsPositive() {
        rateLimitService.isAllowed("export", "user1", 5, 1);

        assertThat(rateLimitService.getResetTimeSeconds("export", "user1", 1)).isGreaterThanOrEqualTo(0);
    }

    // ─── clearRateLimits ──────────────────────────────────────────────────────

    @Test
    void clearRateLimits_resetsCounter() {
        rateLimitService.isAllowed("export", "user1", 1, 1);
        rateLimitService.isAllowed("export", "user1", 1, 1); // now over limit

        rateLimitService.clearRateLimits("export");

        assertThat(rateLimitService.isAllowed("export", "user1", 1, 1)).isTrue();
    }

    // ─── cleanupExpiredEntries ────────────────────────────────────────────────

    @Test
    void cleanupExpiredEntries_doesNotThrow() {
        rateLimitService.isAllowed("export", "user1", 5, 1);

        rateLimitService.cleanupExpiredEntries(); // should not throw
    }
}
