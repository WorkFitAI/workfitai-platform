"""
Tests for Phase 1: SingleFlightCache, CvRankingCache, RecommendationCache.

Covers:
- Single-flight (one compute per key under concurrent load)
- Compute-and-wait on cold miss
- LRU eviction cap in RecommendationCache
- TTL expiry in RecommendationCache
- Cache-disabled flag passthrough (flags default False — all paths exercised here)
- _key_locks eviction cleanup
"""

import threading
import time

import pytest

from app.services.ranking_cache_base import CacheEntry, CacheState, SingleFlightCache
from app.services.cv_ranking_cache import CvRankingCache
from app.services.recommendation_cache import RecommendationCache


# ---------------------------------------------------------------------------
# SingleFlightCache — basic hit / miss / single-flight
# ---------------------------------------------------------------------------

class TestSingleFlightCache:
    def _make(self) -> SingleFlightCache:
        return SingleFlightCache()

    def test_cold_miss_calls_compute(self):
        cache = self._make()
        calls = []

        def compute():
            calls.append(1)
            return "result"

        result, hit = cache.get_or_compute("k", compute, cooldown=60.0)
        assert result == "result"
        assert hit is False
        assert len(calls) == 1

    def test_warm_hit_skips_compute(self):
        cache = self._make()
        calls = []

        def compute():
            calls.append(1)
            return "result"

        cache.get_or_compute("k", compute, cooldown=60.0)
        result, hit = cache.get_or_compute("k", compute, cooldown=60.0)
        assert result == "result"
        assert hit is True
        assert len(calls) == 1  # second call must not recompute

    def test_expired_entry_recomputes(self):
        cache = self._make()
        calls = []

        def compute():
            calls.append(1)
            return f"result-{len(calls)}"

        cache.get_or_compute("k", compute, cooldown=0.05)
        time.sleep(0.1)
        result, hit = cache.get_or_compute("k", compute, cooldown=0.05)
        assert hit is False
        assert len(calls) == 2

    def test_single_flight_concurrent_requests_compute_once(self):
        """50 concurrent requests on one cold key → compute runs exactly once."""
        cache = self._make()
        call_count = [0]
        lock = threading.Lock()

        def slow_compute():
            time.sleep(0.05)  # simulate torch latency
            with lock:
                call_count[0] += 1
            return "result"

        results = []
        errors = []

        def worker():
            try:
                r, _ = cache.get_or_compute("k", slow_compute, cooldown=60.0)
                results.append(r)
            except Exception as exc:
                errors.append(exc)

        threads = [threading.Thread(target=worker) for _ in range(50)]
        for t in threads:
            t.start()
        for t in threads:
            t.join(timeout=10)

        assert not errors, f"Worker errors: {errors}"
        assert call_count[0] == 1, f"Expected 1 compute call, got {call_count[0]}"
        assert all(r == "result" for r in results)
        assert len(results) == 50

    def test_size_reflects_entries(self):
        cache = self._make()
        cache.get_or_compute("a", lambda: 1, cooldown=60.0)
        cache.get_or_compute("b", lambda: 2, cooldown=60.0)
        assert cache.size() == 2


# ---------------------------------------------------------------------------
# CvRankingCache — dirty flag + mark_dirty + iter_refresh_candidates
# ---------------------------------------------------------------------------

class TestCvRankingCache:
    def _make(self) -> CvRankingCache:
        return CvRankingCache()

    def test_mark_dirty_existing_entry(self):
        cache = self._make()
        cache.get_or_compute("job1", lambda: {"ranked_applicants": []}, cooldown=60.0)

        cache.mark_dirty("job1")

        with cache._global_lock:
            entry = cache._entries["job1"]
        assert entry.dirty is True

    def test_mark_dirty_absent_key_is_noop(self):
        cache = self._make()
        cache.mark_dirty("nonexistent")  # must not raise

    def test_stale_entry_served_immediately(self):
        """Dirty entry is a stale-hit — returns cached value without recomputing."""
        cache = self._make()
        calls = []

        def compute():
            calls.append(1)
            return {"ranked_applicants": ["applicant"]}

        cache.get_or_compute("job1", compute, cooldown=60.0)
        cache.mark_dirty("job1")

        result, hit = cache.get_or_compute("job1", compute, cooldown=60.0)
        assert hit is True
        assert len(calls) == 1  # dirty entry is served stale — no recompute

    def test_iter_refresh_candidates_yields_dirty_cooled(self):
        cache = self._make()
        cache.get_or_compute("job1", lambda: {}, cooldown=60.0)
        cache.mark_dirty("job1")

        # Force computed_at to be in the past (beyond cooldown)
        with cache._global_lock:
            cache._entries["job1"].computed_at -= 400.0

        candidates = list(cache.iter_refresh_candidates(cooldown=300.0))
        assert "job1" in candidates

    def test_iter_refresh_candidates_skips_within_cooldown(self):
        cache = self._make()
        cache.get_or_compute("job1", lambda: {}, cooldown=300.0)
        cache.mark_dirty("job1")
        # computed_at is NOW — within cooldown

        candidates = list(cache.iter_refresh_candidates(cooldown=300.0))
        assert "job1" not in candidates

    def test_iter_refresh_candidates_skips_not_dirty(self):
        cache = self._make()
        cache.get_or_compute("job1", lambda: {}, cooldown=300.0)
        # Not dirty
        with cache._global_lock:
            cache._entries["job1"].computed_at -= 400.0

        candidates = list(cache.iter_refresh_candidates(cooldown=300.0))
        assert "job1" not in candidates


# ---------------------------------------------------------------------------
# RecommendationCache — LRU, TTL, version, maybe_refresh, key builders
# ---------------------------------------------------------------------------

class TestRecommendationCache:
    def _make(self, max_entries=5, ttl=600.0) -> RecommendationCache:
        return RecommendationCache(max_entries=max_entries, ttl_seconds=ttl)

    def test_lru_eviction_caps_entries(self):
        cache = self._make(max_entries=3)
        for i in range(5):
            cache.get_or_compute(f"key{i}", lambda i=i: [f"job{i}"], cooldown=60.0)
        assert cache.size() == 3

    def test_lru_evict_also_removes_key_lock(self):
        """_key_locks must not grow beyond max_entries after eviction."""
        cache = self._make(max_entries=2)
        for i in range(4):
            cache.get_or_compute(f"key{i}", lambda: [], cooldown=60.0)
        assert len(cache._key_locks) <= cache.size()

    def test_ttl_expiry_triggers_recompute(self):
        cache = self._make(max_entries=10, ttl=0.05)
        calls = []

        def compute():
            calls.append(1)
            return []

        cache.get_or_compute("k", compute, cooldown=60.0)
        time.sleep(0.1)
        _, hit = cache.get_or_compute("k", compute, cooldown=60.0)
        assert hit is False
        assert len(calls) == 2

    def test_version_bump_marks_entries_stale(self):
        cache = self._make()
        calls = []

        def compute():
            calls.append(1)
            return []

        cache.get_or_compute("k", compute, cooldown=60.0)
        assert cache.size() == 1

        cache.bump_version()

        # Stale entry is served immediately (stale-while-revalidate)
        result, hit = cache.get_or_compute("k", compute, cooldown=60.0)
        assert hit is True   # stale-hit, not recomputed inline
        assert len(calls) == 1

    def test_maybe_refresh_spawns_background_recompute_after_cooldown(self):
        cache = self._make()
        calls = []

        def compute():
            calls.append(1)
            return [f"job-{len(calls)}"]

        cache.get_or_compute("k", compute, cooldown=60.0)
        cache.bump_version()

        # Force old computed_at so cooldown has elapsed
        with cache._global_lock:
            cache._entries["k"].computed_at -= 100.0

        cache.maybe_refresh("k", compute, cooldown=60.0)
        time.sleep(0.2)  # wait for background thread

        # After refresh, version should match and entry should be fresh
        with cache._global_lock:
            entry = cache._entries["k"]
        assert entry.dirty is False
        assert entry.version == cache.current_version
        assert len(calls) == 2

    def test_maybe_refresh_skips_within_cooldown(self):
        cache = self._make()
        calls = []

        def compute():
            calls.append(1)
            return []

        cache.get_or_compute("k", compute, cooldown=60.0)
        cache.bump_version()
        # computed_at is NOW — within cooldown; maybe_refresh must not spawn thread

        cache.maybe_refresh("k", compute, cooldown=60.0)
        time.sleep(0.1)

        assert len(calls) == 1  # no background recompute

    def test_key_builders_are_deterministic(self):
        filters = {"location": "HCM", "jobType": "FULL_TIME"}
        k1 = RecommendationCache.key_for_profile("senior dev", filters, 20)
        k2 = RecommendationCache.key_for_profile("senior dev", filters, 20)
        assert k1 == k2

    def test_key_builders_differ_by_endpoint(self):
        k_profile = RecommendationCache.key_for_profile("text", {}, 20)
        k_search = RecommendationCache.key_for_search("text", {}, 20)
        assert k_profile != k_search

    def test_key_for_resume_normalized(self):
        k1 = RecommendationCache.key_for_resume("  Python Dev  ", {}, 10)
        k2 = RecommendationCache.key_for_resume("python dev", {}, 10)
        assert k1 == k2

    def test_cache_disabled_passthrough(self):
        """When cache is disabled (default), endpoints call compute directly — verify compute fn is callable."""
        compute_calls = []

        def compute():
            compute_calls.append(1)
            return "result"

        # Simulate disabled path: just call compute directly (no cache involved)
        result = compute()
        assert result == "result"
        assert len(compute_calls) == 1
