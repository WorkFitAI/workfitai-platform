"""
In-memory LRU+TTL cache for recommendation endpoint results.

Key: SHA-256 hash of normalized (endpoint, payload) tuple — unbounded input space.
Payload: List[JobRecommendation] — already serialized response objects.
Bounded by MAX_ENTRIES via LRU eviction to prevent memory growth.

Index-version invalidation (Phase 2): any FAISS mutation bumps a global counter;
entries carrying an older version are stale and served stale-while-revalidate.
"""

import hashlib
import json
import logging
import threading
import time
from collections import OrderedDict
from typing import Any, Callable, Dict, List, Optional, Tuple

from app.services.ranking_cache_base import CacheEntry, CacheState, SingleFlightCache

logger = logging.getLogger(__name__)

_cache: Optional["RecommendationCache"] = None


class RecommendationCache(SingleFlightCache):
    """
    Recommendation cache with LRU eviction, TTL, and index-version invalidation.

    Phase 1: cooldown + TTL expiry, LRU eviction, single-flight.
    Phase 2: index_version staleness → stale-while-revalidate + lazy maybe_refresh.
    """

    def __init__(self, max_entries: int = 1000, ttl_seconds: float = 600.0) -> None:
        super().__init__()
        self._max_entries = max_entries
        self._ttl = ttl_seconds
        self._current_version: int = 0
        # Override with OrderedDict for LRU move_to_end support
        self._entries: OrderedDict[str, CacheEntry] = OrderedDict()

    # ------------------------------------------------------------------ #
    # Phase 2: index version management                                   #
    # ------------------------------------------------------------------ #

    @property
    def current_version(self) -> int:
        with self._global_lock:
            return self._current_version

    def bump_version(self) -> None:
        """Increment index version — all existing entries become stale."""
        with self._global_lock:
            self._current_version += 1
        logger.debug("recommendation-cache: version bumped to %d", self._current_version)

    def _get_current_version(self) -> Optional[int]:
        with self._global_lock:
            return self._current_version

    # ------------------------------------------------------------------ #
    # Phase 2: staleness via index version                                #
    # ------------------------------------------------------------------ #

    def _is_stale(self, entry: CacheEntry) -> bool:
        with self._global_lock:
            return entry.version != self._current_version

    # ------------------------------------------------------------------ #
    # TTL expiry (in addition to cooldown)                                #
    # ------------------------------------------------------------------ #

    def _is_ttl_expired(self, entry: CacheEntry, cooldown: float) -> bool:
        age = time.monotonic() - entry.computed_at
        return age >= cooldown or age >= self._ttl

    # ------------------------------------------------------------------ #
    # LRU eviction                                                        #
    # ------------------------------------------------------------------ #

    def _set_entry(self, key: str, entry: CacheEntry) -> None:
        """Insert or update, moving to MRU end. Evict oldest if over cap."""
        self._entries[key] = entry
        self._entries.move_to_end(key)
        if len(self._entries) > self._max_entries:
            evicted_key, _ = self._entries.popitem(last=False)
            self._on_evict(evicted_key)
            logger.debug("recommendation-cache: evicted key=%.16s", evicted_key)

    # ------------------------------------------------------------------ #
    # Phase 2: lazy background refresh for stale hits                    #
    # ------------------------------------------------------------------ #

    def maybe_refresh(
        self,
        key: str,
        compute_fn: Callable[[], Any],
        cooldown: float,
    ) -> None:
        """
        If the entry is stale and cooldown has elapsed since last compute,
        spawn a one-shot background thread to recompute it.
        Single-flight guarded: at most one recompute per key at a time.
        """
        with self._global_lock:
            entry = self._entries.get(key)

        if entry is None or not self._is_stale(entry):
            return
        if (time.monotonic() - entry.computed_at) < cooldown:
            return  # within cooldown — serve stale, no recompute yet

        def _do_refresh() -> None:
            key_lock = self._get_key_lock(key)
            if not key_lock.acquire(blocking=False):
                return  # another thread is already refreshing this key
            try:
                # Re-check staleness under key lock — another thread may have
                # finished a refresh while we were queued up.
                with self._global_lock:
                    current_entry = self._entries.get(key)
                if current_entry is None or not self._is_stale(current_entry):
                    return  # already refreshed; nothing to do

                result = compute_fn()
                new_entry = CacheEntry(
                    result=result,
                    computed_at=time.monotonic(),
                    version=self._get_current_version(),
                    state=CacheState.FRESH,
                    dirty=False,
                )
                with self._global_lock:
                    self._set_entry(key, new_entry)
                logger.info("recommendation-cache: background refresh done key=%.16s", key)
            except Exception as exc:
                logger.error(
                    "recommendation-cache: background refresh failed key=%.16s: %s", key, exc
                )
            finally:
                key_lock.release()

        t = threading.Thread(target=_do_refresh, daemon=True, name=f"rec-refresh-{key[:8]}")
        t.start()

    # ------------------------------------------------------------------ #
    # Key builders                                                        #
    # ------------------------------------------------------------------ #

    @staticmethod
    def _hash(payload: Dict) -> str:
        raw = json.dumps(payload, sort_keys=True, ensure_ascii=False)
        return hashlib.sha256(raw.encode()).hexdigest()

    @staticmethod
    def key_for_resume(formatted_text: str, filters: Dict, top_k: int) -> str:
        return RecommendationCache._hash({
            "endpoint": "by-resume",
            "text": formatted_text.lower().strip(),
            "filters": dict(sorted(filters.items())),
            "top_k": top_k,
        })

    @staticmethod
    def key_for_profile(text: str, filters: Dict, top_k: int) -> str:
        return RecommendationCache._hash({
            "endpoint": "by-profile",
            "text": text.lower().strip(),
            "filters": dict(sorted(filters.items())),
            "top_k": top_k,
        })

    @staticmethod
    def key_for_search(query: str, filters: Dict, top_k: int) -> str:
        return RecommendationCache._hash({
            "endpoint": "search",
            "text": query.lower().strip(),
            "filters": dict(sorted(filters.items())),
            "top_k": top_k,
        })

    @staticmethod
    def key_for_similar(job_id: str, top_k: int, exclude_same_company: bool) -> str:
        return RecommendationCache._hash({
            "endpoint": "similar-jobs",
            "job_id": job_id,
            "top_k": top_k,
            "exclude_same_company": exclude_same_company,
        })


def get_recommendation_cache() -> RecommendationCache:
    """Module-level singleton — mirrors get_store() in cv_refer_store.py."""
    global _cache
    if _cache is None:
        _cache = RecommendationCache()
    return _cache
