"""
In-memory state store for the cv-refer feature.

Maintained by CvReferConsumer via Kafka events — this IS the sync state,
not an extra cache layer.

  job_applicants[jobId]  = set of usernames in the ranking pool
                           (status: APPLIED | REVIEWING | INTERVIEW)

  cv_data[username]      = structured CV fields used as ResumeInput

Thread-safety: reads and writes use a single RLock because
    - writes are rare (Kafka events)
    - reads are per ranking request (not concurrent with writes)
"""

import logging
import threading
from typing import Dict, List, Optional, Set

logger = logging.getLogger(__name__)

# Statuses that keep a candidate in the ranking pool
RANKING_POOL_STATUSES = {"APPLIED", "REVIEWING", "INTERVIEW"}

# Statuses that permanently remove a candidate from the pool
TERMINAL_STATUSES = {"HIRED", "REJECTED"}


class CvReferStore:
    def __init__(self):
        self._lock = threading.RLock()
        # jobId → set of usernames
        self._job_applicants: Dict[str, Set[str]] = {}
        # username → CV structured data
        self._cv_data: Dict[str, Dict] = {}

    # ------------------------------------------------------------------ #
    # Application events                                                   #
    # ------------------------------------------------------------------ #

    def add_applicant(self, job_id: str, username: str) -> None:
        with self._lock:
            self._job_applicants.setdefault(job_id, set()).add(username)
        logger.debug("Store: added applicant %s to job %s (pool size=%d)",
                     username, job_id, len(self._job_applicants[job_id]))

    def remove_applicant(self, job_id: str, username: str) -> None:
        with self._lock:
            pool = self._job_applicants.get(job_id)
            if pool:
                pool.discard(username)
        logger.debug("Store: removed applicant %s from job %s", username, job_id)

    def on_status_changed(self, job_id: str, username: str, new_status: str) -> None:
        """Remove candidate from pool when they reach a terminal status."""
        if new_status.upper() in TERMINAL_STATUSES:
            self.remove_applicant(job_id, username)

    # ------------------------------------------------------------------ #
    # CV data events                                                       #
    # ------------------------------------------------------------------ #

    def update_cv_data(self, username: str, cv: Dict) -> None:
        with self._lock:
            self._cv_data[username] = cv
        logger.debug("Store: updated CV data for %s", username)

    # ------------------------------------------------------------------ #
    # Read access (used by rank-by-job endpoint)                          #
    # ------------------------------------------------------------------ #

    def get_applicant_usernames(self, job_id: str) -> List[str]:
        with self._lock:
            return list(self._job_applicants.get(job_id, set()))

    def get_cv_data(self, username: str) -> Optional[Dict]:
        with self._lock:
            return self._cv_data.get(username)

    def get_stats(self) -> Dict:
        with self._lock:
            return {
                "total_jobs_tracked": len(self._job_applicants),
                "total_cv_profiles": len(self._cv_data),
                "job_pool_sizes": {
                    jid: len(pool) for jid, pool in self._job_applicants.items()
                },
            }


# Singleton — shared between consumer and routes via app.state
_store: Optional[CvReferStore] = None


def get_store() -> CvReferStore:
    global _store
    if _store is None:
        _store = CvReferStore()
    return _store
