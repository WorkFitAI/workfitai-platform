"""
Base in-memory cache with per-key single-flight locking and compute-and-wait.

Pattern mirrors CvReferStore (module-level singleton, threading.RLock).
Subclasses override staleness hooks and LRU eviction.
"""

import logging
import threading
import time
from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Callable, Dict, Optional, Tuple

logger = logging.getLogger(__name__)


class CacheState(str, Enum):
    FRESH = "FRESH"
    COMPUTING = "COMPUTING"


@dataclass
class CacheEntry:
    result: Any              # cached payload (workload-specific type)
    computed_at: float       # time.monotonic() at compute time
    version: Optional[int]   # index_version at compute (recommendations, Phase 2)
    state: CacheState
    dirty: bool              # set by Kafka application events (rank-by-job, Phase 2)


class SingleFlightCache:
    """
    In-memory cache with per-key single-flight locking.

    Phases:
      1 — cooldown-based expiry, compute-and-wait on miss
      2 — dirty/version staleness → stale-while-revalidate + background kick
    """

    def __init__(self) -> None:
        self._entries: Dict[str, CacheEntry] = {}
        self._global_lock = threading.RLock()
        # Per-key locks for single-flight. Cleaned up on LRU eviction via _on_evict.
        self._key_locks: Dict[str, threading.Lock] = {}

    # ------------------------------------------------------------------ #
    # Subclass hooks                                                       #
    # ------------------------------------------------------------------ #

    def _is_stale(self, entry: CacheEntry) -> bool:
        """Phase 2: override to add version/dirty staleness. Phase 1: always False."""
        return False

    def _is_ttl_expired(self, entry: CacheEntry, cooldown: float) -> bool:
        """True when the entry has aged past cooldown. RecommendationCache also checks TTL."""
        return (time.monotonic() - entry.computed_at) >= cooldown

    def _get_current_version(self) -> Optional[int]:
        """Phase 2: return current index version for new entries. Override in RecommendationCache."""
        return None

    def _set_entry(self, key: str, entry: CacheEntry) -> None:
        """Store entry. Override in subclasses for LRU ordering."""
        self._entries[key] = entry

    def _on_evict(self, key: str) -> None:
        """Called when a key is evicted. Cleans up the per-key lock to prevent unbounded growth."""
        self._key_locks.pop(key, None)

    # ------------------------------------------------------------------ #
    # Internal helpers                                                     #
    # ------------------------------------------------------------------ #

    def _get_key_lock(self, key: str) -> threading.Lock:
        with self._global_lock:
            if key not in self._key_locks:
                self._key_locks[key] = threading.Lock()
            return self._key_locks[key]

    def _is_fresh(self, entry: CacheEntry, cooldown: float) -> bool:
        """Entry is usable (not stale by Phase-2 signal, not expired by cooldown)."""
        if self._is_stale(entry):
            return False
        return not self._is_ttl_expired(entry, cooldown)

    # ------------------------------------------------------------------ #
    # Core API                                                             #
    # ------------------------------------------------------------------ #

    def get_or_compute(
        self,
        key: str,
        compute_fn: Callable[[], Any],
        cooldown: float,
    ) -> Tuple[Any, bool]:
        """
        Returns (result, was_hit).

        Behavior:
          - Stale (Phase 2) entry  → return stale result immediately (stale-while-revalidate);
            caller may call maybe_refresh() separately for lazy background recompute.
          - Fresh entry            → hit, return cached result.
          - Expired (not stale)   → acquire per-key lock, compute-and-wait, return fresh result.

        IMPORTANT: caller MUST invoke this via anyio.to_thread.run_sync when called from
        async context, as compute_fn may block the event loop (torch compute).
        """
        # Fast path (no compute)
        with self._global_lock:
            entry = self._entries.get(key)

        if entry is not None:
            if self._is_stale(entry):
                logger.debug("cache=stale-hit key=%.16s", key)
                return entry.result, True
            if self._is_fresh(entry, cooldown):
                logger.debug("cache=hit key=%.16s", key)
                return entry.result, True

        # Slow path: single-flight compute under per-key lock
        key_lock = self._get_key_lock(key)
        with key_lock:
            # Re-check after acquiring lock (another thread may have just computed)
            with self._global_lock:
                entry = self._entries.get(key)

            if entry is not None:
                if self._is_stale(entry):
                    return entry.result, True
                if self._is_fresh(entry, cooldown):
                    return entry.result, True

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

            logger.debug("cache=miss key=%.16s", key)
            return result, False

    def try_hit(self, key: str, cooldown: float) -> Tuple[Any, bool]:
        """
        Non-blocking cache lookup. Returns (result, True) when there is a usable
        entry (fresh or stale-while-revalidate); (None, False) on miss/expired.

        Use this to skip expensive I/O (HTTP calls, store reads) on cache hits
        before falling back to get_or_compute on a miss.
        """
        with self._global_lock:
            entry = self._entries.get(key)
        if entry is None:
            return None, False
        if self._is_stale(entry):
            return entry.result, True   # stale-while-revalidate
        if self._is_fresh(entry, cooldown):
            return entry.result, True   # fresh hit
        return None, False              # TTL-expired, needs recompute

    def force_refresh(self, key: str, compute_fn: Callable[[], Any]) -> Any:
        """
        Unconditionally recompute and store a fresh entry, bypassing stale/TTL checks.

        Used by the background refresher to clear the dirty flag on an entry that was
        served stale-while-revalidate. Single-flight guarded via per-key lock.
        """
        key_lock = self._get_key_lock(key)
        with key_lock:
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
        return result

    # ------------------------------------------------------------------ #
    # Inspection                                                           #
    # ------------------------------------------------------------------ #

    def size(self) -> int:
        with self._global_lock:
            return len(self._entries)
