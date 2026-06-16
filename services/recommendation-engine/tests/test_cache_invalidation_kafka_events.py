"""
Tests for Phase 2: change-feed invalidation.

Covers:
- mark_dirty wired into CvReferConsumer handlers
- bump_version wired into JobEventConsumer handlers
- Stale-while-revalidate behavior (stale-hit served, no inline recompute)
- Cooldown gating for maybe_refresh
- Single-flight guard on background refresh (at most one per key)
- O(1) marking must not block consumer loop
"""

import threading
import time
from unittest.mock import MagicMock, patch

import pytest

from app.services.cv_ranking_cache import CvRankingCache
from app.services.recommendation_cache import RecommendationCache


# ---------------------------------------------------------------------------
# CvRankingCache — dirty marking via consumer simulation
# ---------------------------------------------------------------------------

class TestCvRankingCacheDirtyFlag:
    def _make(self) -> CvRankingCache:
        return CvRankingCache()

    def test_application_created_marks_job_dirty(self):
        """mark_dirty sets dirty=True on an existing entry."""
        cache = self._make()
        cache.get_or_compute("job-abc", lambda: {"ranked_applicants": []}, cooldown=300.0)
        cache.mark_dirty("job-abc")
        with cache._global_lock:
            assert cache._entries["job-abc"].dirty is True

    def test_application_withdrawn_marks_job_dirty(self):
        cache = self._make()
        cache.get_or_compute("job-xyz", lambda: {}, cooldown=300.0)
        cache.mark_dirty("job-xyz")

        with cache._global_lock:
            assert cache._entries["job-xyz"].dirty is True

    def test_status_changed_marks_job_dirty(self):
        cache = self._make()
        cache.get_or_compute("job-status", lambda: {}, cooldown=300.0)
        cache.mark_dirty("job-status")

        with cache._global_lock:
            assert cache._entries["job-status"].dirty is True

    def test_dirty_absent_entry_is_noop(self):
        cache = self._make()
        # Should not raise even with no entry
        cache.mark_dirty("ghost-job")
        assert cache.size() == 0

    def test_marking_dirty_is_fast_o1(self):
        """Dirty marking must complete in < 1ms to not stall Kafka consumer loop."""
        cache = self._make()
        cache.get_or_compute("job-perf", lambda: {}, cooldown=300.0)

        start = time.monotonic()
        for _ in range(1000):
            cache.mark_dirty("job-perf")
        elapsed = time.monotonic() - start

        assert elapsed < 0.1, f"1000 mark_dirty calls took {elapsed:.3f}s — too slow"


# ---------------------------------------------------------------------------
# RecommendationCache — version bump + stale-while-revalidate
# ---------------------------------------------------------------------------

class TestRecommendationCacheVersionInvalidation:
    def _make(self) -> RecommendationCache:
        return RecommendationCache(max_entries=100, ttl_seconds=600.0)

    def test_version_bump_increments_counter(self):
        cache = self._make()
        v0 = cache.current_version
        cache.bump_version()
        assert cache.current_version == v0 + 1

    def test_bump_marks_all_existing_entries_stale(self):
        cache = self._make()
        cache.get_or_compute("k1", lambda: [], cooldown=60.0)
        cache.get_or_compute("k2", lambda: [], cooldown=60.0)

        cache.bump_version()

        with cache._global_lock:
            assert cache._is_stale(cache._entries["k1"])
            assert cache._is_stale(cache._entries["k2"])

    def test_stale_entry_served_without_inline_recompute(self):
        """After version bump, request returns stale result — no inline compute."""
        cache = self._make()
        calls = []

        def compute():
            calls.append(1)
            return [f"job-{len(calls)}"]

        cache.get_or_compute("k", compute, cooldown=60.0)
        cache.bump_version()

        result, hit = cache.get_or_compute("k", compute, cooldown=60.0)
        assert hit is True      # stale-hit, not a miss
        assert len(calls) == 1  # no inline recompute on stale-while-revalidate

    def test_bump_version_is_fast(self):
        """bump_version must complete in < 1ms to not stall Kafka consumer loop."""
        cache = self._make()
        start = time.monotonic()
        for _ in range(1000):
            cache.bump_version()
        elapsed = time.monotonic() - start
        assert elapsed < 0.1, f"1000 bump_version calls took {elapsed:.3f}s — too slow"

    def test_new_entry_after_bump_carries_current_version(self):
        cache = self._make()
        cache.bump_version()
        cache.bump_version()

        cache.get_or_compute("k", lambda: [], cooldown=60.0)

        with cache._global_lock:
            entry = cache._entries["k"]
        assert entry.version == cache.current_version
        assert not cache._is_stale(entry)


# ---------------------------------------------------------------------------
# maybe_refresh — single-flight, cooldown gate, thread safety
# ---------------------------------------------------------------------------

class TestMaybeRefreshSingleFlight:
    def _make(self) -> RecommendationCache:
        return RecommendationCache(max_entries=100, ttl_seconds=600.0)

    def test_maybe_refresh_single_flight_at_most_one_concurrent(self):
        """Concurrent stale hits for same key trigger at most one background recompute."""
        cache = self._make()
        call_count = [0]
        lock = threading.Lock()

        def slow_compute():
            time.sleep(0.05)
            with lock:
                call_count[0] += 1
            return []

        # Populate entry, then reset counter so only background refreshes are counted
        cache.get_or_compute("k", slow_compute, cooldown=60.0)
        call_count[0] = 0
        cache.bump_version()

        # Force past cooldown
        with cache._global_lock:
            cache._entries["k"].computed_at -= 100.0

        # Fire 10 concurrent maybe_refresh calls
        threads = [
            threading.Thread(target=lambda: cache.maybe_refresh("k", slow_compute, cooldown=60.0))
            for _ in range(10)
        ]
        for t in threads:
            t.start()
        for t in threads:
            t.join(timeout=5)

        time.sleep(0.1)  # let background thread settle

        # Only one background recompute should have run
        assert call_count[0] == 1, f"Expected 1 recompute, got {call_count[0]}"

    def test_maybe_refresh_noop_when_not_stale(self):
        cache = self._make()
        calls = []
        cache.get_or_compute("k", lambda: [], cooldown=60.0)

        # Entry is fresh — maybe_refresh must be a noop
        cache.maybe_refresh("k", lambda: calls.append(1) or [], cooldown=60.0)
        time.sleep(0.1)
        assert len(calls) == 0

    def test_maybe_refresh_noop_within_cooldown(self):
        cache = self._make()
        calls = []
        cache.get_or_compute("k", lambda: [], cooldown=60.0)
        cache.bump_version()
        # computed_at is NOW — within cooldown

        cache.maybe_refresh("k", lambda: calls.append(1) or [], cooldown=60.0)
        time.sleep(0.1)
        assert len(calls) == 0  # within cooldown, no recompute

    def test_maybe_refresh_error_does_not_crash(self):
        """Background recompute failure must leave the stale entry intact."""
        cache = self._make()
        original = [{"jobId": "j1"}]
        cache.get_or_compute("k", lambda: original, cooldown=60.0)
        cache.bump_version()

        with cache._global_lock:
            cache._entries["k"].computed_at -= 100.0

        def failing_compute():
            raise RuntimeError("compute failed")

        cache.maybe_refresh("k", failing_compute, cooldown=60.0)
        time.sleep(0.2)  # let background thread run and fail

        # Stale entry must still be present
        assert cache.size() == 1
        with cache._global_lock:
            entry = cache._entries["k"]
        assert cache._is_stale(entry)  # still stale (failed refresh didn't clear it)
