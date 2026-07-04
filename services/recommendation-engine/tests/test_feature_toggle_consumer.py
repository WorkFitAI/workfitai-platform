"""Tests for app/kafka_consumer/feature_toggle_consumer.py (FeatureToggleConsumer)."""

from unittest.mock import MagicMock

import pytest
from kafka.errors import KafkaError

from app.kafka_consumer.feature_toggle_consumer import FeatureToggleConsumer
from app.services.feature_toggle_store import FeatureToggleStore


def _kafka_config(**overrides):
    config = {
        "bootstrap_servers": "kafka:9092",
        "group_id": "recommendation-engine-feature-toggle",
        "topic_feature_toggle": "platform-feature-toggle-events",
    }
    config.update(overrides)
    return config


@pytest.fixture
def store():
    return FeatureToggleStore()


@pytest.fixture
def consumer(store):
    return FeatureToggleConsumer(_kafka_config(), store)


class TestDispatch:
    def test_updates_store_on_valid_event(self, consumer, store):
        consumer._dispatch({"featureKey": "job-recommendation", "enabled": False})
        assert store.get("job-recommendation") is False

    def test_enabled_true_updates_store(self, consumer, store):
        store.set("cv-referral", False)
        consumer._dispatch({"featureKey": "cv-referral", "enabled": True})
        assert store.get("cv-referral") is True

    def test_missing_feature_key_is_malformed_and_ignored(self, consumer, store):
        consumer._dispatch({"enabled": True})
        # Untouched keys still default to enabled per store's documented behavior.
        assert store.get("job-recommendation") is True

    def test_missing_enabled_is_malformed_and_ignored(self, consumer, store):
        consumer._dispatch({"featureKey": "job-recommendation"})
        assert store.get("job-recommendation") is True


class TestLifecycle:
    def test_connect_success(self, consumer, monkeypatch):
        fake = MagicMock()
        monkeypatch.setattr(
            "app.kafka_consumer.feature_toggle_consumer.KafkaConsumer",
            MagicMock(return_value=fake),
        )
        assert consumer.connect() is True
        assert consumer._consumer is fake

    def test_connect_failure(self, consumer, monkeypatch):
        def _raise(*a, **kw):
            raise KafkaError("refused")

        monkeypatch.setattr(
            "app.kafka_consumer.feature_toggle_consumer.KafkaConsumer", _raise
        )
        assert consumer.connect() is False

    def test_start_already_running_is_noop(self, consumer):
        consumer._running = True
        consumer.start()
        assert consumer._thread is None

    def test_start_connect_failure_does_not_start(self, consumer, monkeypatch):
        monkeypatch.setattr(consumer, "connect", lambda: False)
        consumer.start()
        assert consumer._running is False

    def test_start_and_stop_lifecycle(self, consumer, monkeypatch):
        fake = MagicMock()
        fake.poll.return_value = {}
        monkeypatch.setattr(
            "app.kafka_consumer.feature_toggle_consumer.KafkaConsumer",
            MagicMock(return_value=fake),
        )
        consumer.start()
        assert consumer._running is True
        consumer.stop()
        assert consumer._running is False
        fake.close.assert_called_once()
