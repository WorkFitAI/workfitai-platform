"""
Kafka consumer for job events (created/updated/expired/deleted).

Each FAISS mutation also bumps the recommendation cache index_version (Phase 2)
so stale recommendation entries are served-then-refreshed on next request.
"""

import json
import logging
import threading
from typing import Dict

from kafka import KafkaConsumer
from kafka.errors import KafkaError

from app.services.field_format import JOB_FIELDS

logger = logging.getLogger(__name__)


class JobEventConsumer:
    """
    Consume job events from Kafka and update FAISS index.
    On each successful mutation, bumps recommendation cache index_version.
    """

    def __init__(self, kafka_config: Dict, faiss_manager, job_formatter, embedding_generator):
        self.faiss_manager = faiss_manager
        self.job_formatter = job_formatter
        self.embedding_generator = embedding_generator
        self.running = False
        self.consumer = None
        self.consumer_thread = None

        self.bootstrap_servers = kafka_config.get("bootstrap_servers", "localhost:9092")
        self.group_id = kafka_config.get("group_id", "recommendation-engine")
        self.topics = [
            kafka_config.get("topic_job_created", "job.created"),
            kafka_config.get("topic_job_updated", "job.updated"),
            kafka_config.get("topic_job_deleted", "job.deleted"),
            kafka_config.get("topic_job_expired", "job.expired"),
        ]

        logger.info("JobEventConsumer initialized for topics: %s", self.topics)

    def connect(self) -> bool:
        try:
            self.consumer = KafkaConsumer(
                *self.topics,
                bootstrap_servers=self.bootstrap_servers,
                group_id=self.group_id,
                value_deserializer=lambda m: json.loads(m.decode('utf-8')),
                auto_offset_reset='earliest',
                enable_auto_commit=True,
                session_timeout_ms=30000,
                heartbeat_interval_ms=10000
            )
            logger.info("✓ Connected to Kafka at %s", self.bootstrap_servers)
            logger.info("✓ Subscribed to topics: %s", self.topics)
            return True
        except KafkaError as exc:
            logger.error("Failed to connect to Kafka: %s", exc)
            return False

    def start_consuming(self) -> None:
        if self.running:
            logger.warning("Consumer already running")
            return
        if not self.consumer and not self.connect():
            logger.error("Cannot start consumer — connection failed")
            return
        self.running = True
        self.consumer_thread = threading.Thread(target=self._consume_loop, daemon=True)
        self.consumer_thread.start()
        logger.info("✓ Kafka consumer started in background thread")

    def _consume_loop(self) -> None:
        logger.info("Starting Kafka consumption loop...")
        try:
            while self.running:
                messages = self.consumer.poll(timeout_ms=1000)
                for topic_partition, records in messages.items():
                    for record in records:
                        try:
                            self._handle_message(record.topic, record.value)
                        except Exception as exc:
                            logger.error(
                                "Error processing message from %s: %s", record.topic, exc, exc_info=True
                            )
        except Exception as exc:
            logger.error("Fatal error in consume loop: %s", exc, exc_info=True)
        finally:
            logger.info("Kafka consume loop stopped")

    def _handle_message(self, topic: str, event: Dict) -> None:
        event_type = event.get("eventType")
        logger.info("Received event: %s from topic: %s", event_type, topic)

        if "created" in topic or event_type == "JOB_CREATED":
            self.handle_job_created(event)
        elif "updated" in topic or event_type == "JOB_UPDATED":
            self.handle_job_updated(event)
        elif "expired" in topic or event_type == "JOB_EXPIRED":
            self.handle_job_expired(event)
        elif "deleted" in topic or event_type == "JOB_DELETED":
            self.handle_job_deleted(event)
        else:
            logger.warning("Unknown event type: %s", event_type)

    # ------------------------------------------------------------------ #
    # Event handlers                                                       #
    # ------------------------------------------------------------------ #

    def _encode_and_store(self, job_id: str, job_data: Dict, store) -> None:
        """Shared multi-field encode + FAISS store logic for create/update.

        `store` is faiss_manager.add_job_with_embedding or
        .update_job_with_embedding (same field_embeddings/field_mask contract)."""
        fields = self.job_formatter.format_job_as_fields(job_data)
        pooled_embedding, per_field_embeddings, mask = self.embedding_generator.encode_job_fields(
            fields, JOB_FIELDS
        )
        field_mask = dict(zip(JOB_FIELDS, mask))
        store(job_id, pooled_embedding, job_data, field_embeddings=per_field_embeddings, field_mask=field_mask)

    def handle_job_created(self, event: Dict) -> None:
        try:
            job_id = event.get("jobId")
            job_data = event.get("data", {})
            logger.info("Processing JOB_CREATED for jobId: %s", job_id)

            self._encode_and_store(job_id, job_data, self.faiss_manager.add_job_with_embedding)

            logger.info("✓ Added job %s to FAISS index", job_id)
            self._bump_recommendation_cache()
        except Exception as exc:
            logger.error("Error handling job created event: %s", exc, exc_info=True)

    def handle_job_updated(self, event: Dict) -> None:
        try:
            job_id = event.get("jobId")
            job_data = event.get("data", {})
            logger.info("Processing JOB_UPDATED for jobId: %s", job_id)

            self._encode_and_store(job_id, job_data, self.faiss_manager.update_job_with_embedding)

            logger.info("✓ Updated job %s in FAISS index", job_id)
            self._bump_recommendation_cache()
        except Exception as exc:
            logger.error("Error handling job updated event: %s", exc, exc_info=True)

    def handle_job_expired(self, event: Dict) -> None:
        """Treated the same as deletion -- an expired job should no longer
        appear in recommendations, and FAISS has no "soft remove" concept."""
        try:
            job_id = event.get("jobId")
            logger.info("Processing JOB_EXPIRED for jobId: %s", job_id)

            self.faiss_manager.remove_job(job_id)

            logger.info("✓ Removed expired job %s from FAISS index", job_id)
            self._bump_recommendation_cache()
        except Exception as exc:
            logger.error("Error handling job expired event: %s", exc, exc_info=True)

    def handle_job_deleted(self, event: Dict) -> None:
        try:
            job_id = event.get("jobId")
            reason = event.get("reason", "Unknown")
            logger.info("Processing JOB_DELETED for jobId: %s, reason: %s", job_id, reason)

            self.faiss_manager.remove_job(job_id)

            logger.info("✓ Removed job %s from FAISS index", job_id)
            self._bump_recommendation_cache()
        except Exception as exc:
            logger.error("Error handling job deleted event: %s", exc, exc_info=True)

    def _bump_recommendation_cache(self) -> None:
        """Invalidate all cached recommendation results after a FAISS mutation."""
        try:
            from app.config import get_settings
            if not get_settings().RECOMMENDATION_CACHE_ENABLED:
                return
            from app.services.recommendation_cache import get_recommendation_cache
            get_recommendation_cache().bump_version()
        except Exception as exc:
            logger.warning("Failed to bump recommendation cache version: %s", exc)

    # ------------------------------------------------------------------ #
    # Lifecycle                                                            #
    # ------------------------------------------------------------------ #

    def stop(self) -> None:
        logger.info("Stopping Kafka consumer...")
        self.running = False
        if self.consumer_thread:
            self.consumer_thread.join(timeout=5)
        if self.consumer:
            self.consumer.close()
        logger.info("✓ Kafka consumer stopped")

    def get_stats(self) -> Dict:
        return {
            "running": self.running,
            "topics": self.topics,
            "group_id": self.group_id
        }
