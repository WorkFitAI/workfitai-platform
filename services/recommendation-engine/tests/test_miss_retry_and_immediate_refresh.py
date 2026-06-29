"""
Tests for Phase 4: miss -> 202 retry-later kickoff, and immediate refresh on dirty events.

Covers:
- SingleFlightCache.try_start_compute / finish_compute (non-blocking claim + release)
- CvRankingCache.is_due_for_refresh (single-key cooldown gate used by trigger_immediate)
- CvRankingRefresher.trigger_immediate (cooldown-gated, schedules onto the event loop)
"""

import asyncio
import time
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.services.cv_ranking_cache import CvRankingCache
from app.services.cv_ranking_refresher import CvRankingRefresher
from app.services.ranking_cache_base import SingleFlightCache


# ---------------------------------------------------------------------------
# SingleFlightCache — try_start_compute / finish_compute
# ---------------------------------------------------------------------------

class TestTryStartComputeFinishCompute:
    def _make(self) -> SingleFlightCache:
        return SingleFlightCache()

    def test_first_caller_claims_slot(self):
        cache = self._make()
        assert cache.try_start_compute("k") is True

    def test_second_caller_blocked_while_in_flight(self):
        cache = self._make()
        assert cache.try_start_compute("k") is True
        assert cache.try_start_compute("k") is False  # already claimed

    def test_finish_compute_stores_result_and_releases_slot(self):
        cache = self._make()
        cache.try_start_compute("k")
        cache.finish_compute("k", {"value": 1})

        result, hit = cache.get_or_compute("k", lambda: {"value": 2}, cooldown=60.0)
        assert result == {"value": 1}
        assert hit is True  # served from the entry finish_compute stored, no recompute

        # Slot released — a new claim must succeed
        assert cache.try_start_compute("k") is True

    def test_finish_compute_with_none_leaves_no_entry(self):
        """A failed background compute (result=None) must not poison the cache with a gap."""
        cache = self._make()
        cache.try_start_compute("k")
        cache.finish_compute("k", None)

        assert cache.size() == 0
        # Slot released — a retry must be able to claim it
        assert cache.try_start_compute("k") is True

    def test_different_keys_do_not_block_each_other(self):
        cache = self._make()
        assert cache.try_start_compute("k1") is True
        assert cache.try_start_compute("k2") is True


# ---------------------------------------------------------------------------
# CvRankingCache.is_due_for_refresh
# ---------------------------------------------------------------------------

class TestIsDueForRefresh:
    def _make(self) -> CvRankingCache:
        return CvRankingCache()

    def test_absent_entry_is_not_due(self):
        cache = self._make()
        assert cache.is_due_for_refresh("ghost", cooldown=60.0) is False

    def test_clean_entry_is_not_due(self):
        cache = self._make()
        cache.get_or_compute("job1", lambda: {}, cooldown=60.0)
        assert cache.is_due_for_refresh("job1", cooldown=60.0) is False

    def test_dirty_within_cooldown_is_not_due(self):
        cache = self._make()
        cache.get_or_compute("job1", lambda: {}, cooldown=300.0)
        cache.mark_dirty("job1")
        # computed_at is NOW — within cooldown
        assert cache.is_due_for_refresh("job1", cooldown=300.0) is False

    def test_dirty_past_cooldown_is_due(self):
        cache = self._make()
        cache.get_or_compute("job1", lambda: {}, cooldown=300.0)
        cache.mark_dirty("job1")
        with cache._global_lock:
            cache._entries["job1"].computed_at -= 400.0
        assert cache.is_due_for_refresh("job1", cooldown=300.0) is True


# ---------------------------------------------------------------------------
# CvRankingRefresher.trigger_immediate
# ---------------------------------------------------------------------------

def _make_settings(cooldown: int = 300) -> MagicMock:
    s = MagicMock()
    s.CV_RANKING_REFRESH_INTERVAL_SECONDS = 9999
    s.CV_RANKING_CACHE_COOLDOWN_SECONDS = cooldown
    s.JOB_SERVICE_URL = "http://fake-job-service"
    s.JOB_SERVICE_TIMEOUT = 5
    return s


class TestTriggerImmediate:
    @pytest.mark.asyncio
    async def test_noop_before_start(self):
        """trigger_immediate before start() must not raise (no event loop captured yet)."""
        cache = CvRankingCache()
        cache.get_or_compute("job1", lambda: {}, cooldown=300.0)
        cache.mark_dirty("job1")
        with cache._global_lock:
            cache._entries["job1"].computed_at -= 400.0

        refresher = CvRankingRefresher(
            cache=cache, store=MagicMock(), pipeline_getter=lambda: MagicMock(is_ready=True),
            settings=_make_settings(),
        )
        refresher.trigger_immediate("job1")  # must not raise

    @pytest.mark.asyncio
    async def test_within_cooldown_does_not_schedule_refresh(self):
        cache = CvRankingCache()
        cache.get_or_compute("job1", lambda: {"ranked_applicants": []}, cooldown=300.0)
        cache.mark_dirty("job1")
        # computed_at is NOW — within cooldown

        settings = _make_settings(cooldown=300)
        refresher = CvRankingRefresher(
            cache=cache, store=MagicMock(), pipeline_getter=lambda: MagicMock(is_ready=True),
            settings=settings,
        )
        refresher.start()
        try:
            fetch_mock = AsyncMock()
            with patch("app.services.cv_ranking_refresher.fetch_job_data", new=fetch_mock):
                refresher.trigger_immediate("job1")
                await asyncio.sleep(0.05)
            fetch_mock.assert_not_called()
        finally:
            await refresher.stop()

    @pytest.mark.asyncio
    async def test_due_for_refresh_schedules_and_clears_dirty(self):
        cache = CvRankingCache()
        cache.get_or_compute("job1", lambda: {"ranked_applicants": []}, cooldown=5.0)
        cache.mark_dirty("job1")
        with cache._global_lock:
            cache._entries["job1"].computed_at -= 400.0  # past any cooldown

        store = MagicMock()
        store.get_applicant_usernames.return_value = ["user1"]
        store.get_cv_data.return_value = {
            "summary": "Python dev", "experience": "5yr", "skills": "Python", "education": "BSc"
        }

        settings = _make_settings(cooldown=300)
        refresher = CvRankingRefresher(
            cache=cache, store=store, pipeline_getter=lambda: MagicMock(is_ready=True),
            settings=settings,
        )
        refresher.start()
        try:
            with patch("app.services.cv_ranking_refresher.fetch_job_data", new=AsyncMock(return_value={
                "title": "SWE", "requirements": "Python", "responsibilities": "", "benefits": "", "description": ""
            })), patch("app.services.cv_ranking_refresher.rank_resumes", return_value={
                "job_overview": "new", "total_candidates": 1, "ranked_resumes": [],
            }):
                refresher.trigger_immediate("job1")
                # Give the scheduled coroutine a few ticks to complete (async + thread hop)
                for _ in range(20):
                    await asyncio.sleep(0.05)
                    with cache._global_lock:
                        if cache._entries["job1"].dirty is False:
                            break
            with cache._global_lock:
                assert cache._entries["job1"].dirty is False
        finally:
            await refresher.stop()
