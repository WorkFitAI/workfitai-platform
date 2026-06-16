"""
Kafka consumer for cv-refer feature.

Subscribed topics:
  application-events  — APPLICATION_CREATED, APPLICATION_WITHDRAWN
  application-status  — STATUS_CHANGED

CV structured fields (summary, experience, skills, education) are now embedded
directly in APPLICATION_CREATED events by application-service (which calls cv-service
during the SNAPSHOT_CV saga step). No additional HTTP call is needed at ranking time.
"""

import json
import logging
import threading
from typing import Dict

from kafka import KafkaConsumer
from kafka.errors import KafkaError

from app.services.cv_refer_store import CvReferStore

logger = logging.getLogger(__name__)


class CvReferConsumer:
    def __init__(self, kafka_config: Dict, store: CvReferStore):
        self._store = store
        self._running = False
        self._consumer = None
        self._thread = None

        self._bootstrap_servers = kafka_config["bootstrap_servers"]
        self._group_id = kafka_config["group_id"]
        self._topics = [
            kafka_config["topic_application_events"],
            kafka_config["topic_application_status"],
        ]
        logger.info("CvReferConsumer initialised for topics: %s", self._topics)

    # ------------------------------------------------------------------ #
    # Lifecycle                                                            #
    # ------------------------------------------------------------------ #

    def connect(self) -> bool:
        try:
            self._consumer = KafkaConsumer(
                *self._topics,
                bootstrap_servers=self._bootstrap_servers,
                group_id=self._group_id,
                value_deserializer=lambda m: json.loads(m.decode("utf-8")),
                auto_offset_reset="earliest",
                enable_auto_commit=True,
                session_timeout_ms=30_000,
                heartbeat_interval_ms=10_000,
            )
            logger.info("CvReferConsumer connected to Kafka at %s", self._bootstrap_servers)
            return True
        except KafkaError as e:
            logger.error("CvReferConsumer failed to connect: %s", e)
            return False

    def start(self) -> None:
        if self._running:
            return
        if not self._consumer and not self.connect():
            logger.error("CvReferConsumer cannot start — connection failed")
            return
        self._running = True
        self._thread = threading.Thread(target=self._consume_loop, daemon=True, name="cv-refer-consumer")
        self._thread.start()
        logger.info("CvReferConsumer started")

    def stop(self) -> None:
        logger.info("Stopping CvReferConsumer...")
        self._running = False
        if self._thread:
            self._thread.join(timeout=5)
        if self._consumer:
            self._consumer.close()
        logger.info("CvReferConsumer stopped")

    # ------------------------------------------------------------------ #
    # Consumption loop                                                     #
    # ------------------------------------------------------------------ #

    def _consume_loop(self) -> None:
        try:
            while self._running:
                messages = self._consumer.poll(timeout_ms=1_000)
                for tp, records in messages.items():
                    for record in records:
                        try:
                            self._dispatch(record.topic, record.value)
                        except Exception as e:
                            logger.error("Error processing message from %s: %s", record.topic, e, exc_info=True)
        except Exception as e:
            logger.error("Fatal error in CvReferConsumer loop: %s", e, exc_info=True)
        finally:
            logger.info("CvReferConsumer loop exited")

    def _dispatch(self, topic: str, event: Dict) -> None:
        event_type = event.get("eventType", "")

        if event_type == "APPLICATION_CREATED":
            self._on_application_created(event)
        elif event_type == "APPLICATION_WITHDRAWN":
            self._on_application_withdrawn(event)
        elif event_type == "STATUS_CHANGED":
            self._on_status_changed(event)
        else:
            logger.debug("CvReferConsumer: ignored event_type=%s topic=%s", event_type, topic)

    # ------------------------------------------------------------------ #
    # Event handlers                                                       #
    # ------------------------------------------------------------------ #

    def _on_application_created(self, event: Dict) -> None:
        """
        Handle APPLICATION_CREATED event.

        Since application-service now embeds CV snapshot fields (summary, experience,
        skills, education) directly in the event payload (populated from cv-service
        during the SNAPSHOT_CV saga step), we can populate both the applicant pool
        and the cv_data store in a single handler — no extra HTTP call needed.
        """
        data = event.get("data", {})
        job_id = data.get("jobId")
        username = data.get("username")
        if not job_id or not username:
            return

        # Add to applicant pool
        self._store.add_applicant(job_id, username)

        # Populate CV snapshot from event payload (best-effort: may be empty strings if
        # cv-service was unavailable during snapshot creation, ranking will still work)
        snapshot = {
            "summary":    data.get("resumeSummary", ""),
            "experience": data.get("resumeExperience", ""),
            "skills":     data.get("resumeSkills", ""),
            "education":  data.get("resumeEducation", ""),
        }

        # Only store if there's at least some non-empty field to avoid polluting the store
        # with empty records that would cause unnecessary cache misses
        if any(snapshot.values()):
            self._store.set_cv_snapshot(job_id, username, snapshot)
            logger.info(
                "cv-refer: APPLICATION_CREATED — jobId=%s username=%s (snapshot stored, cvSnapshotId=%s)",
                job_id, username, data.get("cvSnapshotId", "none"),
            )
        else:
            logger.warning(
                "cv-refer: APPLICATION_CREATED — jobId=%s username=%s — CV snapshot fields empty "
                "(cv-service may have been unavailable during SNAPSHOT_CV step)",
                job_id, username,
            )

        # Applicant pool changed — cached ranking for this job is now stale
        self._mark_ranking_dirty(job_id)

    def _on_application_withdrawn(self, event: Dict) -> None:
        data = event.get("data", {})
        job_id = data.get("jobId")
        username = data.get("username")
        if job_id and username:
            self._store.remove_applicant(job_id, username)
            logger.info("cv-refer: application withdrawn — jobId=%s username=%s", job_id, username)
            self._mark_ranking_dirty(job_id)

    def _on_status_changed(self, event: Dict) -> None:
        data = event.get("data", {})
        job_id = data.get("jobId")
        username = data.get("username")
        new_status = data.get("newStatus", "")
        if job_id and username:
            self._store.on_status_changed(job_id, username, new_status)
            logger.info("cv-refer: status changed — jobId=%s username=%s newStatus=%s", job_id, username, new_status)
            self._mark_ranking_dirty(job_id)

    def _mark_ranking_dirty(self, job_id: str) -> None:
        """Mark cached ranking for job_id as dirty (application pool changed)."""
        try:
            from app.config import get_settings
            if not get_settings().CV_RANKING_CACHE_ENABLED:
                return
            from app.services.cv_ranking_cache import get_cv_ranking_cache
            get_cv_ranking_cache().mark_dirty(job_id)
        except Exception as exc:
            logger.warning("Failed to mark ranking cache dirty for job %s: %s", job_id, exc)
