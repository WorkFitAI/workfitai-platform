"""
Background refresher for rank-by-job cache entries.

Proactively recomputes dirty + cooled-down entries so the next HR request
hits a warm, fresh cache instead of blocking on an inline pipeline run.

Design constraints:
- Single asyncio task (not one per key) — mirrors consumer lifecycle in lifespan.
- Torch compute runs via anyio.to_thread.run_sync — never blocks the event loop.
- At most one recompute per key at a time (single-flight via cache's key lock).
- Serializes recomputes to avoid GPU/CPU contention with live requests.
- Loop crash on one key is isolated — logs error, continues to next candidate.
"""

import asyncio
import logging
from typing import Optional

import anyio

from app.services.cv_ranking_cache import CvRankingCache
from app.services.cv_refer_store import CvReferStore
from app.services.cv_rank_assembly import build_cv_rank_request, fetch_job_data
from app.models.cv_rank_responses import RankedApplicantResponse

logger = logging.getLogger(__name__)


def rank_resumes(pipeline, request):
    """Lazy shim — defers torch import to first call; module-level name is patchable."""
    from app.services.cv_ranking_service import rank_resumes as _fn
    return _fn(pipeline, request)


class CvRankingRefresher:
    """
    Asyncio-task-based background refresher for the rank-by-job cache.

    start() / stop() are called from FastAPI lifespan (main.py).
    """

    def __init__(
        self,
        cache: CvRankingCache,
        store: CvReferStore,
        pipeline_getter,   # callable → CVRankingPipeline | None
        settings,
    ) -> None:
        self._cache = cache
        self._store = store
        self._pipeline_getter = pipeline_getter
        self._settings = settings
        self._task: Optional[asyncio.Task] = None

    def start(self) -> None:
        if self._task is not None:
            return
        self._task = asyncio.create_task(self._loop(), name="cv-ranking-refresher")
        logger.info("CvRankingRefresher started (interval=%ds)", self._settings.CV_RANKING_REFRESH_INTERVAL_SECONDS)

    async def stop(self) -> None:
        if self._task is None:
            return
        self._task.cancel()
        try:
            await asyncio.wait_for(asyncio.shield(self._task), timeout=5.0)
        except (asyncio.CancelledError, asyncio.TimeoutError):
            pass
        self._task = None
        logger.info("CvRankingRefresher stopped")

    # ------------------------------------------------------------------ #
    # Main loop                                                            #
    # ------------------------------------------------------------------ #

    async def _loop(self) -> None:
        interval = self._settings.CV_RANKING_REFRESH_INTERVAL_SECONDS
        cooldown = float(self._settings.CV_RANKING_CACHE_COOLDOWN_SECONDS)

        if interval > cooldown:
            logger.warning(
                "CV_RANKING_REFRESH_INTERVAL_SECONDS (%d) > CV_RANKING_CACHE_COOLDOWN_SECONDS (%d) "
                "— refresher will be lazier than optimal; tune after load test.",
                interval, int(cooldown),
            )

        while True:
            try:
                await asyncio.sleep(interval)
                await self._tick(cooldown)
            except asyncio.CancelledError:
                logger.info("CvRankingRefresher loop cancelled")
                return
            except Exception as exc:
                logger.error("CvRankingRefresher loop error (continuing): %s", exc, exc_info=True)

    async def _tick(self, cooldown: float) -> None:
        """One refresh sweep: find dirty+cooled candidates, recompute each."""
        pipeline = self._pipeline_getter()
        if pipeline is None or not pipeline.is_ready:
            logger.debug("CvRankingRefresher: pipeline not ready, skipping tick")
            return

        candidates = list(self._cache.iter_refresh_candidates(cooldown))
        if not candidates:
            return

        logger.info("CvRankingRefresher: %d candidate(s) to refresh", len(candidates))

        for job_id in candidates:
            try:
                await self._refresh_one(job_id, pipeline, cooldown)
            except Exception as exc:
                # Isolated per-key — loop must not die on one failure
                logger.error("CvRankingRefresher: failed to refresh job_id=%s: %s", job_id, exc, exc_info=True)

    async def _refresh_one(self, job_id: str, pipeline, cooldown: float) -> None:
        """Fetch job data, build request, recompute, store in cache."""
        job_data = await fetch_job_data(
            self._settings.JOB_SERVICE_URL, job_id, self._settings.JOB_SERVICE_TIMEOUT
        )
        if job_data is None:
            logger.warning("CvRankingRefresher: could not fetch job data for job_id=%s, skipping", job_id)
            return

        cv_rank_request, idx_to_username, reason = build_cv_rank_request(
            job_id, self._store, job_data, options=None
        )
        if cv_rank_request is None:
            logger.info("CvRankingRefresher: skipping job_id=%s — %s", job_id, reason)
            return

        def _compute() -> dict:
            result = rank_resumes(pipeline, cv_rank_request)
            applicants = [
                RankedApplicantResponse(
                    username=idx_to_username.get(r["resume_index"], "unknown"),
                    score=r["score"],
                    similarity_score=r["similarity_score"],
                    cross_score=r["cross_score"],
                    label=r["label"],
                    explanation=r["explanation"],
                )
                for r in result["ranked_resumes"]
            ]
            return {
                "job_overview": result["job_overview"],
                "total_candidates": result["total_candidates"],
                "ranked_applicants": applicants,
            }

        # force_refresh bypasses stale check and clears dirty=True
        payload = await anyio.to_thread.run_sync(
            lambda: self._cache.force_refresh(job_id, _compute)
        )

        logger.info(
            "CvRankingRefresher: refreshed job_id=%s ranked=%d",
            job_id, len(payload.get("ranked_applicants", [])),
        )
