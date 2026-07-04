"""Direct unit tests for app/services/feature_toggle_store.py."""

from app.services.feature_toggle_store import FeatureToggleStore, get_feature_toggle_store


class TestFeatureToggleStore:
    def test_unknown_key_defaults_to_enabled(self):
        store = FeatureToggleStore()
        assert store.get("job-recommendation") is True

    def test_set_then_get_reflects_value(self):
        store = FeatureToggleStore()
        store.set("cv-referral", False)
        assert store.get("cv-referral") is False

    def test_snapshot_returns_copy_of_all_toggles(self):
        store = FeatureToggleStore()
        store.set("job-recommendation", False)
        store.set("cv-referral", True)
        snapshot = store.snapshot()
        assert snapshot == {"job-recommendation": False, "cv-referral": True}
        # Mutating the snapshot must not affect internal state.
        snapshot["job-recommendation"] = True
        assert store.get("job-recommendation") is False


class TestSingleton:
    def test_get_feature_toggle_store_returns_same_instance(self):
        import app.services.feature_toggle_store as module

        module._store = None
        first = get_feature_toggle_store()
        second = get_feature_toggle_store()
        assert first is second
        module._store = None
