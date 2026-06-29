"""
In-memory cache for rank-by-job results.

Key: job_id (bounded — one entry per open job in the applicant pool).
Payload: dict { job_overview, total_candidates, ranked_applicants: List[RankedApplicantResponse] }
         Usernames are pre-resolved so the payload is self-contained.

Dirty flag set by Kafka application events (Phase 2).
Proactive refresh driven by CvRankingRefresher loop (Phase 3).
"""

import logging
import time
from typing import Iterator, Optional

from app.services.ranking_cache_base import CacheEntry, SingleFlightCache

logger = logging.getLogger(__name__)

_cache: Optional["CvRankingCache"] = None


class CvRankingCache(SingleFlightCache):
    """
    Rank-by-job cache keyed by job_id.

    Phase 1: cooldown-based expiry + single-flight.
    Phase 2: dirty flag → stale-while-revalidate.
    Phase 3: iter_refresh_candidates() feeds the background refresher.
    """

    # ------------------------------------------------------------------ #
    # Phase 2: staleness via dirty flag                                   #
    # ------------------------------------------------------------------ #

    def _is_stale(self, entry: CacheEntry) -> bool:
        return entry.dirty

    def mark_dirty(self, job_id: str) -> None:
        """
        Mark a cached ranking as dirty when an application event arrives.
        No-op if job_id is not in cache (entry will be computed fresh on next request).
        """
        with self._global_lock:
            entry = self._entries.get(job_id)
            if entry is not None:
                entry.dirty = True
                logger.debug("cv-rank-cache: marked dirty job_id=%s", job_id)

    # ------------------------------------------------------------------ #
    # Phase 3: background refresher candidates                            #
    # ------------------------------------------------------------------ #

    def is_due_for_refresh(self, job_id: str, cooldown: float) -> bool:
        """
        True when job_id is dirty AND has cooled long enough to recompute now.

        Single-key version of iter_refresh_candidates() — lets a Kafka handler ask
        "should I trigger an immediate refresh for this one job?" without scanning
        the whole cache.
        """
        with self._global_lock:
            entry = self._entries.get(job_id)
        if entry is None:
            return False
        return entry.dirty and (time.monotonic() - entry.computed_at) >= cooldown

    def iter_refresh_candidates(self, cooldown: float) -> Iterator[str]:
        """
        Yield job_ids that are dirty AND have cooled long enough to recompute.
        Snapshots under lock so iteration is safe while other threads mutate.
        """
        now = time.monotonic()
        with self._global_lock:
            snapshot = list(self._entries.items())

        for job_id, entry in snapshot:
            if entry.dirty and (now - entry.computed_at) >= cooldown:
                yield job_id


def get_cv_ranking_cache() -> CvRankingCache:
    """Module-level singleton — mirrors get_store() in cv_refer_store.py."""
    global _cache
    if _cache is None:
        _cache = CvRankingCache()
    return _cache
