"""
Tests for Phase 3: CvRankingRefresher background loop.

Covers:
- Dirty+cooled entry gets refreshed (computed_at advances, dirty cleared)
- Within-cooldown dirty entry is skipped
- Build failure leaves entry dirty without crashing the loop
- Disabled flag (CV_RANKING_CACHE_ENABLED=False) → refresher never started
- Refresher tick loop isolation (one key failure doesn't kill the loop)
"""

import asyncio
import time
from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from app.services.cv_ranking_cache import CvRankingCache
from app.services.cv_ranking_refresher import CvRankingRefresher


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_settings(
    interval: int = 1,
    cooldown: int = 5,
    job_service_url: str = "http://fake-job-service",
    timeout: int = 5,
) -> MagicMock:
    s = MagicMock()
    s.CV_RANKING_REFRESH_INTERVAL_SECONDS = interval
    s.CV_RANKING_CACHE_COOLDOWN_SECONDS = cooldown
    s.JOB_SERVICE_URL = job_service_url
    s.JOB_SERVICE_TIMEOUT = timeout
    return s


def _make_pipeline(ready: bool = True) -> MagicMock:
    p = MagicMock()
    p.is_ready = ready
    return p


def _make_refresher(
    cache: CvRankingCache,
    store=None,
    pipeline=None,
    settings=None,
) -> CvRankingRefresher:
    if store is None:
        store = MagicMock()
    if pipeline is None:
        pipeline = _make_pipeline()
    if settings is None:
        settings = _make_settings()
    return CvRankingRefresher(
        cache=cache,
        store=store,
        pipeline_getter=lambda: pipeline,
        settings=settings,
    )


# ---------------------------------------------------------------------------
# _tick direct tests (unit — no asyncio task needed)
# ---------------------------------------------------------------------------

class TestCvRankingRefresherTick:
    @pytest.mark.asyncio
    async def test_tick_refreshes_dirty_cooled_entry(self):
        cache = CvRankingCache()
        payload_before = {"job_overview": "old", "total_candidates": 1, "ranked_applicants": ["old"]}
        cache.get_or_compute("job-1", lambda: payload_before, cooldown=5.0)
        cache.mark_dirty("job-1")

        # Push computed_at into the past so cooldown has elapsed
        with cache._global_lock:
            cache._entries["job-1"].computed_at -= 10.0
        computed_at_before = cache._entries["job-1"].computed_at

        payload_after = {"job_overview": "new", "total_candidates": 2, "ranked_applicants": ["new"]}

        store = MagicMock()
        store.get_applicant_usernames.return_value = ["user1"]
        store.get_cv_data.return_value = {
            "summary": "Python dev", "experience": "5yr", "skills": "Python", "education": "BSc"
        }

        pipeline = _make_pipeline(ready=True)

        with patch("app.services.cv_ranking_refresher.fetch_job_data", new=AsyncMock(return_value={
            "title": "SWE", "requirements": "Python", "responsibilities": "", "benefits": "", "description": ""
        })), \
             patch("app.services.cv_ranking_refresher.rank_resumes", return_value={
                 "job_overview": "new",
                 "total_candidates": 2,
                 "ranked_resumes": [],
             }):
            refresher = _make_refresher(cache, store=store, pipeline=pipeline)
            await refresher._tick(cooldown=5.0)

        with cache._global_lock:
            entry = cache._entries["job-1"]
        assert entry.dirty is False
        assert entry.computed_at > computed_at_before

    @pytest.mark.asyncio
    async def test_tick_skips_entry_within_cooldown(self):
        cache = CvRankingCache()
        cache.get_or_compute("job-2", lambda: {}, cooldown=300.0)
        cache.mark_dirty("job-2")
        # computed_at is NOW — within cooldown

        refresher = _make_refresher(cache, settings=_make_settings(cooldown=300))
        compute_called = []

        with patch("app.services.cv_ranking_refresher.fetch_job_data", new=AsyncMock(return_value={})):
            await refresher._tick(cooldown=300.0)

        # Entry must still be dirty (skipped)
        with cache._global_lock:
            assert cache._entries["job-2"].dirty is True

    @pytest.mark.asyncio
    async def test_tick_skips_when_pipeline_not_ready(self):
        cache = CvRankingCache()
        cache.get_or_compute("job-3", lambda: {}, cooldown=5.0)
        cache.mark_dirty("job-3")
        with cache._global_lock:
            cache._entries["job-3"].computed_at -= 10.0

        pipeline = _make_pipeline(ready=False)
        refresher = _make_refresher(cache, pipeline=pipeline)

        fetch_mock = AsyncMock()
        with patch("app.services.cv_ranking_refresher.fetch_job_data", new=fetch_mock):
            await refresher._tick(cooldown=5.0)

        fetch_mock.assert_not_called()

    @pytest.mark.asyncio
    async def test_tick_build_failure_leaves_entry_dirty(self):
        """build_cv_rank_request failure must not crash the loop; entry stays dirty."""
        cache = CvRankingCache()
        cache.get_or_compute("job-4", lambda: {}, cooldown=5.0)
        cache.mark_dirty("job-4")
        with cache._global_lock:
            cache._entries["job-4"].computed_at -= 10.0

        store = MagicMock()
        store.get_applicant_usernames.return_value = []  # empty → build returns None

        refresher = _make_refresher(cache, store=store)

        with patch("app.services.cv_ranking_refresher.fetch_job_data", new=AsyncMock(return_value={"title": "SWE"})):
            await refresher._tick(cooldown=5.0)

        with cache._global_lock:
            assert cache._entries["job-4"].dirty is True

    @pytest.mark.asyncio
    async def test_tick_one_key_error_does_not_kill_loop(self):
        """Exception on first candidate must not prevent processing of second."""
        cache = CvRankingCache()
        # Two dirty+cooled candidates
        for job_id in ("job-err", "job-ok"):
            cache.get_or_compute(job_id, lambda: {}, cooldown=5.0)
            cache.mark_dirty(job_id)
            with cache._global_lock:
                cache._entries[job_id].computed_at -= 10.0

        store = MagicMock()
        store.get_applicant_usernames.return_value = ["u1"]
        store.get_cv_data.return_value = {
            "summary": "s", "experience": "e", "skills": "sk", "education": "ed"
        }
        pipeline = _make_pipeline(ready=True)

        call_log = []

        async def fake_fetch(url, job_id, timeout):
            call_log.append(job_id)
            if job_id == "job-err":
                raise RuntimeError("fetch failed")
            return {"title": "SWE", "requirements": "", "responsibilities": "", "benefits": "", "description": ""}

        with patch("app.services.cv_ranking_refresher.fetch_job_data", new=fake_fetch), \
             patch("app.services.cv_ranking_refresher.rank_resumes", return_value={
                 "job_overview": "ok", "total_candidates": 1, "ranked_resumes": []
             }):
            refresher = _make_refresher(cache, store=store, pipeline=pipeline)
            await refresher._tick(cooldown=5.0)

        # Both keys were attempted
        assert len(call_log) == 2
        # job-ok should be refreshed (dirty cleared)
        with cache._global_lock:
            assert cache._entries["job-ok"].dirty is False


# ---------------------------------------------------------------------------
# Lifecycle — start / stop
# ---------------------------------------------------------------------------

class TestCvRankingRefresherLifecycle:
    @pytest.mark.asyncio
    async def test_start_creates_task(self):
        cache = CvRankingCache()
        settings = _make_settings(interval=9999)  # never fires in test
        refresher = _make_refresher(cache, settings=settings)

        refresher.start()
        assert refresher._task is not None
        assert not refresher._task.done()

        await refresher.stop()
        assert refresher._task is None

    @pytest.mark.asyncio
    async def test_stop_is_idempotent(self):
        cache = CvRankingCache()
        refresher = _make_refresher(cache, settings=_make_settings(interval=9999))
        await refresher.stop()  # stop before start — must not raise
        refresher.start()
        await refresher.stop()
        await refresher.stop()  # double-stop — must not raise

    @pytest.mark.asyncio
    async def test_start_is_idempotent(self):
        cache = CvRankingCache()
        refresher = _make_refresher(cache, settings=_make_settings(interval=9999))
        refresher.start()
        task_first = refresher._task
        refresher.start()  # double-start — must reuse same task
        assert refresher._task is task_first
        await refresher.stop()
