"""
Tests for app/kafka_consumer/cv_refer_consumer.py (CvReferConsumer).

Dispatch handlers are invoked directly with crafted event dicts. The store is a
real (in-memory) CvReferStore — cheaper and more faithful than mocking it.
"""

from unittest.mock import MagicMock

import numpy as np
import pytest
from kafka.errors import KafkaError

from app.kafka_consumer.cv_refer_consumer import CvReferConsumer
from app.services.cv_refer_store import CvReferStore


def _kafka_config(**overrides):
    config = {
        "bootstrap_servers": "kafka:9092",
        "group_id": "recommendation-engine-cv-refer",
        "topic_application_events": "application-events",
        "topic_application_status": "application-status",
    }
    config.update(overrides)
    return config


@pytest.fixture
def store():
    return CvReferStore()


@pytest.fixture
def refresher():
    return MagicMock(name="refresher")


@pytest.fixture
def pipeline():
    mock = MagicMock(name="pipeline")
    mock.is_ready = True
    mock.encode_resume.return_value = np.zeros(4, dtype=np.float32)
    return mock


@pytest.fixture
def consumer(store, refresher, pipeline):
    return CvReferConsumer(
        _kafka_config(),
        store,
        refresher_getter=lambda: refresher,
        pipeline_getter=lambda: pipeline,
    )


class TestApplicationCreated:
    def test_populates_pool_and_cv_snapshot(self, consumer, store):
        event = {
            "eventType": "APPLICATION_CREATED",
            "data": {
                "jobId": "job-1",
                "username": "alice",
                "resumeSummary": "Backend engineer",
                "resumeExperience": "5 years",
                "resumeSkills": "Python",
                "resumeEducation": "BSc",
                "cvSnapshotId": "snap-1",
            },
        }
        consumer._dispatch("application-events", event)
        assert "alice" in store.get_applicant_usernames("job-1")
        assert store.get_cv_data("job-1", "alice")["summary"] == "Backend engineer"

    def test_missing_job_id_or_username_is_ignored(self, consumer, store):
        consumer._dispatch("application-events", {"eventType": "APPLICATION_CREATED", "data": {}})
        assert store.get_applicant_usernames("job-1") == []

    def test_empty_snapshot_still_stores_applicant(self, consumer, store):
        event = {
            "eventType": "APPLICATION_CREATED",
            "data": {"jobId": "job-2", "username": "bob"},
        }
        consumer._dispatch("application-events", event)
        assert "bob" in store.get_applicant_usernames("job-2")
        assert store.get_cv_data("job-2", "bob") == {
            "summary": "", "experience": "", "skills": "", "education": "",
        }

    def test_precomputes_embedding_when_snapshot_nonempty_and_pipeline_ready(
        self, consumer, store
    ):
        event = {
            "eventType": "APPLICATION_CREATED",
            "data": {"jobId": "job-3", "username": "carol", "resumeSummary": "Has content"},
        }
        consumer._dispatch("application-events", event)
        assert store.get_cv_embedding("job-3", "carol") is not None

    def test_embedding_skipped_when_pipeline_not_ready(self, store, refresher):
        pipeline = MagicMock()
        pipeline.is_ready = False
        c = CvReferConsumer(
            _kafka_config(), store, refresher_getter=lambda: refresher, pipeline_getter=lambda: pipeline
        )
        event = {
            "eventType": "APPLICATION_CREATED",
            "data": {"jobId": "job-4", "username": "dave", "resumeSummary": "content"},
        }
        c._dispatch("application-events", event)
        assert store.get_cv_embedding("job-4", "dave") is None

    def test_embedding_precompute_failure_is_non_fatal(self, consumer, store, pipeline):
        pipeline.encode_resume.side_effect = RuntimeError("model failure")
        event = {
            "eventType": "APPLICATION_CREATED",
            "data": {"jobId": "job-5", "username": "erin", "resumeSummary": "content"},
        }
        consumer._dispatch("application-events", event)  # must not raise
        assert store.get_cv_embedding("job-5", "erin") is None

    def test_marks_ranking_dirty_and_triggers_refresh(self, consumer, store, refresher, monkeypatch):
        from app.config import get_settings

        monkeypatch.setattr(get_settings(), "CV_RANKING_CACHE_ENABLED", True)
        event = {
            "eventType": "APPLICATION_CREATED",
            "data": {"jobId": "job-6", "username": "frank"},
        }
        consumer._dispatch("application-events", event)
        refresher.trigger_immediate.assert_called_once_with("job-6")

    def test_dirty_marking_skipped_when_cache_disabled(self, consumer, refresher, monkeypatch):
        from app.config import get_settings

        monkeypatch.setattr(get_settings(), "CV_RANKING_CACHE_ENABLED", False)
        event = {
            "eventType": "APPLICATION_CREATED",
            "data": {"jobId": "job-7", "username": "gary"},
        }
        consumer._dispatch("application-events", event)
        refresher.trigger_immediate.assert_not_called()

    def test_dirty_marking_failure_is_non_fatal(self, consumer, refresher, monkeypatch):
        from app.config import get_settings

        monkeypatch.setattr(get_settings(), "CV_RANKING_CACHE_ENABLED", True)
        refresher.trigger_immediate.side_effect = RuntimeError("boom")
        event = {
            "eventType": "APPLICATION_CREATED",
            "data": {"jobId": "job-8", "username": "hank"},
        }
        consumer._dispatch("application-events", event)  # must not raise

    def test_does_not_overwrite_snapshot_from_earlier_ollama_push(self, consumer, store):
        # Regression: cv-service kicks off an async Ollama re-extraction the moment the
        # snapshot CV is created (before APPLICATION_CREATED is even published) and pushes
        # corrected sections straight into this store via POST /internal/cv-refer/snapshot.
        # That push can beat this Kafka event to the consumer — if this handler blindly
        # overwrote, the better Ollama data would be silently clobbered back to the
        # heuristic (often blank) event payload.
        store.set_cv_snapshot("job-9", "ivan", {
            "summary": "Ollama-corrected summary",
            "experience": "Ollama-corrected experience",
            "skills": "Ollama-corrected skills",
            "education": "Ollama-corrected education",
        })
        event = {
            "eventType": "APPLICATION_CREATED",
            "data": {
                "jobId": "job-9", "username": "ivan",
                "resumeSummary": "", "resumeExperience": "",
                "resumeSkills": "", "resumeEducation": "",
            },
        }

        consumer._dispatch("application-events", event)

        assert store.get_cv_data("job-9", "ivan")["summary"] == "Ollama-corrected summary"
        assert "ivan" in store.get_applicant_usernames("job-9")


class TestApplicationWithdrawn:
    def test_removes_applicant(self, consumer, store):
        store.add_applicant("job-1", "alice")
        event = {"eventType": "APPLICATION_WITHDRAWN", "data": {"jobId": "job-1", "username": "alice"}}
        consumer._dispatch("application-events", event)
        assert "alice" not in store.get_applicant_usernames("job-1")

    def test_missing_ids_is_ignored(self, consumer, store):
        consumer._dispatch("application-events", {"eventType": "APPLICATION_WITHDRAWN", "data": {}})


class TestStatusChanged:
    def test_terminal_status_removes_applicant(self, consumer, store):
        store.add_applicant("job-1", "alice")
        event = {
            "eventType": "STATUS_CHANGED",
            "data": {"jobId": "job-1", "username": "alice", "newStatus": "HIRED"},
        }
        consumer._dispatch("application-status", event)
        assert "alice" not in store.get_applicant_usernames("job-1")

    def test_non_terminal_status_keeps_applicant(self, consumer, store):
        store.add_applicant("job-1", "alice")
        event = {
            "eventType": "STATUS_CHANGED",
            "data": {"jobId": "job-1", "username": "alice", "newStatus": "REVIEWING"},
        }
        consumer._dispatch("application-status", event)
        assert "alice" in store.get_applicant_usernames("job-1")


class TestUnknownEventType:
    def test_ignored(self, consumer, store):
        consumer._dispatch("application-events", {"eventType": "SOMETHING_ELSE", "data": {}})


class TestLifecycle:
    def test_connect_success(self, consumer, monkeypatch):
        fake = MagicMock()
        monkeypatch.setattr(
            "app.kafka_consumer.cv_refer_consumer.KafkaConsumer", MagicMock(return_value=fake)
        )
        assert consumer.connect() is True
        assert consumer._consumer is fake

    def test_connect_failure(self, consumer, monkeypatch):
        def _raise(*a, **kw):
            raise KafkaError("refused")

        monkeypatch.setattr("app.kafka_consumer.cv_refer_consumer.KafkaConsumer", _raise)
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
            "app.kafka_consumer.cv_refer_consumer.KafkaConsumer", MagicMock(return_value=fake)
        )
        consumer.start()
        assert consumer._running is True
        consumer.stop()
        assert consumer._running is False
        fake.close.assert_called_once()
