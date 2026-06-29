"""
In-memory state store for the cv-refer feature.

Maintained by CvReferConsumer via Kafka events — this IS the sync state,
not an extra cache layer.

  job_applicants[jobId]          = set of usernames in the ranking pool
                                   (status: APPLIED | REVIEWING | INTERVIEW)

  cv_data[(jobId, username)]     = structured CV fields extracted by cv-service at apply time
                                   Keys: summary, experience, skills, education
                                   Keyed by (jobId, username) so different applications
                                   to different jobs can carry different CV snapshots.

Thread-safety: reads and writes use a single RLock.
"""

import logging
import threading
from typing import Any, Dict, List, Optional, Set, Tuple

logger = logging.getLogger(__name__)

RANKING_POOL_STATUSES = {"APPLIED", "REVIEWING", "INTERVIEW"}
TERMINAL_STATUSES = {"HIRED", "REJECTED"}

# Key type for cv_data dict
_CvKey = str  # formatted as "{jobId}::{username}"


def _cv_key(job_id: str, username: str) -> _CvKey:
    return f"{job_id}::{username}"


class CvReferStore:
    def __init__(self):
        self._lock = threading.RLock()
        # jobId → set of usernames
        self._job_applicants: Dict[str, Set[str]] = {}
        # "{jobId}::{username}" → CV snapshot dict
        self._cv_data: Dict[_CvKey, Dict] = {}
        # "{jobId}::{username}" → precomputed bi-encoder embedding (np.ndarray)
        # Optional cache: absence just means stage 1 falls back to encoding on the
        # fly, same as before this cache existed. Computed once when the snapshot
        # text is known (Kafka consumer / reconciliation push), not at rank time.
        self._embeddings: Dict[_CvKey, Any] = {}

    # ------------------------------------------------------------------ #
    # Application events                                                   #
    # ------------------------------------------------------------------ #

    def add_applicant(self, job_id: str, username: str) -> None:
        with self._lock:
            self._job_applicants.setdefault(job_id, set()).add(username)
        logger.debug("Store: added applicant %s to job %s (pool size=%d)",
                     username, job_id, len(self._job_applicants[job_id]))

    def set_cv_snapshot(self, job_id: str, username: str, snapshot: Dict) -> None:
        """
        Store structured CV fields for a specific application.

        Invalidates any cached embedding for this key — the text changed, so a
        stale embedding would silently rank against the old snapshot. Callers
        that have a bi-encoder handy should follow up with set_cv_embedding().
        """
        with self._lock:
            self._cv_data[_cv_key(job_id, username)] = snapshot
            self._embeddings.pop(_cv_key(job_id, username), None)
        logger.debug("Store: cv snapshot set for %s / %s", job_id, username)

    def set_cv_embedding(self, job_id: str, username: str, embedding: Any) -> None:
        """Cache a precomputed bi-encoder embedding for this applicant's resume text."""
        with self._lock:
            self._embeddings[_cv_key(job_id, username)] = embedding

    def get_cv_embedding(self, job_id: str, username: str) -> Optional[Any]:
        with self._lock:
            return self._embeddings.get(_cv_key(job_id, username))

    def remove_applicant(self, job_id: str, username: str) -> None:
        with self._lock:
            pool = self._job_applicants.get(job_id)
            if pool:
                pool.discard(username)
            self._cv_data.pop(_cv_key(job_id, username), None)
            self._embeddings.pop(_cv_key(job_id, username), None)
        logger.debug("Store: removed applicant %s from job %s", username, job_id)

    def on_status_changed(self, job_id: str, username: str, new_status: str) -> None:
        if new_status.upper() in TERMINAL_STATUSES:
            self.remove_applicant(job_id, username)

    # ------------------------------------------------------------------ #
    # Read access (used by rank-by-job endpoint)                          #
    # ------------------------------------------------------------------ #

    def get_applicant_usernames(self, job_id: str) -> List[str]:
        with self._lock:
            return list(self._job_applicants.get(job_id, set()))

    def get_cv_data(self, job_id: str, username: str) -> Optional[Dict]:
        with self._lock:
            return self._cv_data.get(_cv_key(job_id, username))

    def get_stats(self) -> Dict:
        with self._lock:
            return {
                "total_jobs_tracked": len(self._job_applicants),
                "total_cv_profiles": len(self._cv_data),
                "total_cv_embeddings": len(self._embeddings),
                "job_pool_sizes": {
                    jid: len(pool) for jid, pool in self._job_applicants.items()
                },
            }


_store: Optional[CvReferStore] = None


def get_store() -> CvReferStore:
    global _store
    if _store is None:
        _store = CvReferStore()
    return _store
