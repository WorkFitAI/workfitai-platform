"""
Kafka consumer for platform-wide AI feature toggle changes.

Subscribed topic: platform-feature-toggle-events
Payload: {"featureKey": str, "enabled": bool, "updatedAt": str}

Mirrors CvReferConsumer's connect/start/stop/_consume_loop structure.
"""

import json
import logging
import threading
from typing import Dict

from kafka import KafkaConsumer
from kafka.errors import KafkaError

from app.services.feature_toggle_store import FeatureToggleStore

logger = logging.getLogger(__name__)


class FeatureToggleConsumer:
    def __init__(self, kafka_config: Dict, store: FeatureToggleStore):
        self._store = store
        self._running = False
        self._consumer = None
        self._thread = None

        self._bootstrap_servers = kafka_config["bootstrap_servers"]
        self._group_id = kafka_config["group_id"]
        self._topic = kafka_config["topic_feature_toggle"]
        logger.info("FeatureToggleConsumer initialised for topic: %s", self._topic)

    def connect(self) -> bool:
        try:
            self._consumer = KafkaConsumer(
                self._topic,
                bootstrap_servers=self._bootstrap_servers,
                group_id=self._group_id,
                value_deserializer=lambda m: json.loads(m.decode("utf-8")),
                auto_offset_reset="earliest",
                enable_auto_commit=True,
                session_timeout_ms=30_000,
                heartbeat_interval_ms=10_000,
            )
            logger.info("FeatureToggleConsumer connected to Kafka at %s", self._bootstrap_servers)
            return True
        except KafkaError as e:
            logger.error("FeatureToggleConsumer failed to connect: %s", e)
            return False

    def start(self) -> None:
        if self._running:
            return
        if not self._consumer and not self.connect():
            logger.error("FeatureToggleConsumer cannot start — connection failed")
            return
        self._running = True
        self._thread = threading.Thread(
            target=self._consume_loop, daemon=True, name="feature-toggle-consumer"
        )
        self._thread.start()
        logger.info("FeatureToggleConsumer started")

    def stop(self) -> None:
        logger.info("Stopping FeatureToggleConsumer...")
        self._running = False
        if self._thread:
            self._thread.join(timeout=5)
        if self._consumer:
            self._consumer.close()
        logger.info("FeatureToggleConsumer stopped")

    def _consume_loop(self) -> None:
        try:
            while self._running:
                messages = self._consumer.poll(timeout_ms=1_000)
                for tp, records in messages.items():
                    for record in records:
                        try:
                            self._dispatch(record.value)
                        except Exception as e:
                            logger.error("Error processing feature toggle message: %s", e, exc_info=True)
        except Exception as e:
            logger.error("Fatal error in FeatureToggleConsumer loop: %s", e, exc_info=True)
        finally:
            logger.info("FeatureToggleConsumer loop exited")

    def _dispatch(self, event: Dict) -> None:
        feature_key = event.get("featureKey")
        enabled = event.get("enabled")
        if feature_key is None or enabled is None:
            logger.warning("FeatureToggleConsumer: malformed event %s", event)
            return

        self._store.set(feature_key, bool(enabled))
        logger.info("Feature toggle updated: %s=%s", feature_key, enabled)
