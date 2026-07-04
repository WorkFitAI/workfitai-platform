"""
Tests for app/kafka_consumer/consumer.py (JobEventConsumer).

Handlers are invoked directly with crafted event dicts (no real Kafka broker).
connect()/start_consuming()/stop() lifecycle tests patch the `KafkaConsumer`
class imported into this module so no real network connection is attempted.
"""

from unittest.mock import MagicMock

import pytest
from kafka.errors import KafkaError

from app.kafka_consumer.consumer import JobEventConsumer


def _kafka_config(**overrides):
    config = {
        "bootstrap_servers": "kafka:9092",
        "group_id": "recommendation-engine",
        "topic_job_created": "job.created",
        "topic_job_updated": "job.updated",
        "topic_job_deleted": "job.deleted",
        "topic_job_expired": "job.expired",
    }
    config.update(overrides)
    return config


@pytest.fixture
def consumer():
    faiss_manager = MagicMock(name="faiss_manager")
    job_formatter = MagicMock(name="job_formatter")
    job_formatter.format_job_as_text.return_value = "formatted job text"
    embedding_generator = MagicMock(name="embedding_generator")
    embedding_generator.encode_job.return_value = [0.1, 0.2]
    return JobEventConsumer(_kafka_config(), faiss_manager, job_formatter, embedding_generator)


class TestEventHandlers:
    def test_handle_job_created_adds_to_faiss(self, consumer):
        event = {"jobId": "job-1", "data": {"title": "Backend Engineer"}}
        consumer.handle_job_created(event)
        consumer.faiss_manager.add_job_with_embedding.assert_called_once_with(
            "job-1", [0.1, 0.2], {"title": "Backend Engineer"}
        )

    def test_handle_job_created_error_is_caught(self, consumer):
        consumer.faiss_manager.add_job_with_embedding.side_effect = RuntimeError("boom")
        # Must not raise.
        consumer.handle_job_created({"jobId": "job-1", "data": {}})

    def test_handle_job_updated_updates_faiss(self, consumer):
        event = {"jobId": "job-2", "data": {"title": "Updated"}}
        consumer.handle_job_updated(event)
        consumer.faiss_manager.update_job_with_embedding.assert_called_once_with(
            "job-2", [0.1, 0.2], {"title": "Updated"}
        )

    def test_handle_job_updated_error_is_caught(self, consumer):
        consumer.faiss_manager.update_job_with_embedding.side_effect = RuntimeError("boom")
        consumer.handle_job_updated({"jobId": "job-2", "data": {}})

    def test_handle_job_deleted_removes_from_faiss(self, consumer):
        event = {"jobId": "job-3", "reason": "expired"}
        consumer.handle_job_deleted(event)
        consumer.faiss_manager.remove_job.assert_called_once_with("job-3")

    def test_handle_job_deleted_error_is_caught(self, consumer):
        consumer.faiss_manager.remove_job.side_effect = RuntimeError("boom")
        consumer.handle_job_deleted({"jobId": "job-3"})

    def test_bump_recommendation_cache_invalidates_when_enabled(self, consumer, monkeypatch):
        from app.config import get_settings
        from app.services.recommendation_cache import get_recommendation_cache

        monkeypatch.setattr(get_settings(), "RECOMMENDATION_CACHE_ENABLED", True)
        cache = get_recommendation_cache()
        before = cache.current_version
        consumer._bump_recommendation_cache()
        assert cache.current_version == before + 1

    def test_bump_recommendation_cache_noop_when_disabled(self, consumer, monkeypatch):
        from app.config import get_settings

        monkeypatch.setattr(get_settings(), "RECOMMENDATION_CACHE_ENABLED", False)
        # Must not raise even though cache module is untouched.
        consumer._bump_recommendation_cache()

    def test_bump_recommendation_cache_failure_is_non_fatal(self, consumer, monkeypatch):
        monkeypatch.setattr(
            "app.config.get_settings", lambda: (_ for _ in ()).throw(RuntimeError("boom"))
        )
        consumer._bump_recommendation_cache()


class TestDispatch:
    def test_dispatch_created_by_topic_name(self, consumer):
        consumer.handle_job_created = MagicMock()
        consumer._handle_message("job.created", {"eventType": "SOMETHING_ELSE"})
        consumer.handle_job_created.assert_called_once()

    def test_dispatch_created_by_event_type(self, consumer):
        consumer.handle_job_created = MagicMock()
        consumer._handle_message("unrelated-topic", {"eventType": "JOB_CREATED"})
        consumer.handle_job_created.assert_called_once()

    def test_dispatch_updated(self, consumer):
        consumer.handle_job_updated = MagicMock()
        consumer._handle_message("job.updated", {"eventType": "JOB_UPDATED"})
        consumer.handle_job_updated.assert_called_once()

    def test_dispatch_deleted(self, consumer):
        consumer.handle_job_deleted = MagicMock()
        consumer._handle_message("job.deleted", {"eventType": "JOB_DELETED"})
        consumer.handle_job_deleted.assert_called_once()

    def test_dispatch_unknown_event_type_logs_warning(self, consumer):
        consumer.handle_job_created = MagicMock()
        consumer.handle_job_updated = MagicMock()
        consumer.handle_job_deleted = MagicMock()
        consumer._handle_message("some-other-topic", {"eventType": "MYSTERY"})
        consumer.handle_job_created.assert_not_called()
        consumer.handle_job_updated.assert_not_called()
        consumer.handle_job_deleted.assert_not_called()


class TestLifecycle:
    def test_connect_success(self, consumer, monkeypatch):
        fake_kafka_consumer = MagicMock()
        monkeypatch.setattr(
            "app.kafka_consumer.consumer.KafkaConsumer", MagicMock(return_value=fake_kafka_consumer)
        )
        assert consumer.connect() is True
        assert consumer.consumer is fake_kafka_consumer

    def test_connect_failure_returns_false(self, consumer, monkeypatch):
        def _raise(*a, **kw):
            raise KafkaError("connection refused")

        monkeypatch.setattr("app.kafka_consumer.consumer.KafkaConsumer", _raise)
        assert consumer.connect() is False

    def test_start_consuming_already_running_is_noop(self, consumer):
        consumer.running = True
        consumer.consumer_thread = None
        consumer.start_consuming()
        assert consumer.consumer_thread is None

    def test_start_consuming_connect_failure_does_not_start_thread(self, consumer, monkeypatch):
        monkeypatch.setattr(consumer, "connect", lambda: False)
        consumer.start_consuming()
        assert consumer.running is False
        assert consumer.consumer_thread is None

    def test_start_consuming_and_stop_lifecycle(self, consumer, monkeypatch):
        fake_kafka_consumer = MagicMock()
        fake_kafka_consumer.poll.return_value = {}
        monkeypatch.setattr(
            "app.kafka_consumer.consumer.KafkaConsumer", MagicMock(return_value=fake_kafka_consumer)
        )
        consumer.start_consuming()
        assert consumer.running is True
        assert consumer.consumer_thread is not None
        consumer.stop()
        assert consumer.running is False
        fake_kafka_consumer.close.assert_called_once()

    def test_get_stats(self, consumer):
        stats = consumer.get_stats()
        assert stats["group_id"] == "recommendation-engine"
        assert stats["running"] is False
        assert set(stats["topics"]) == {"job.created", "job.updated", "job.deleted"}
