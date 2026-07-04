"""
In-memory cache of admin-controlled platform feature toggles
(job-recommendation, cv-referral). Updated by FeatureToggleConsumer,
read by route handlers before serving AI recommendations/rankings.

Unknown keys (not yet seen via Kafka, e.g. at cold start before the first
message arrives) default to enabled - documented behavior, not a bug.
"""

import threading
from typing import Dict, Optional


class FeatureToggleStore:
    def __init__(self):
        self._lock = threading.Lock()
        self._toggles: Dict[str, bool] = {}

    def get(self, feature_key: str) -> bool:
        with self._lock:
            return self._toggles.get(feature_key, True)

    def set(self, feature_key: str, enabled: bool) -> None:
        with self._lock:
            self._toggles[feature_key] = enabled

    def snapshot(self) -> Dict[str, bool]:
        with self._lock:
            return dict(self._toggles)


_store: Optional[FeatureToggleStore] = None


def get_feature_toggle_store() -> FeatureToggleStore:
    global _store
    if _store is None:
        _store = FeatureToggleStore()
    return _store
