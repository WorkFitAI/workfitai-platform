"""
Kafka consumer for job events (created/updated/deleted)
"""

import logging
import json
from typing import Dict
from kafka import KafkaConsumer
from kafka.errors import KafkaError
import threading

logger = logging.getLogger(__name__)


class JobEventConsumer:
    """
    Consume job events from Kafka and update FAISS index
    """
    
    def __init__(self, kafka_config: Dict, faiss_manager, job_formatter, embedding_generator):
        """Initialize Kafka consumer"""
        self.faiss_manager = faiss_manager
        self.job_formatter = job_formatter
        self.embedding_generator = embedding_generator
        self.running = False
        self.consumer = None
        self.consumer_thread = None
        
        # Kafka configuration
        self.bootstrap_servers = kafka_config.get("bootstrap_servers", "localhost:9092")
        self.group_id = kafka_config.get("group_id", "recommendation-engine")
        self.topics = [
            kafka_config.get("topic_job_created", "job.created"),
            kafka_config.get("topic_job_updated", "job.updated"),
            kafka_config.get("topic_job_deleted", "job.deleted")
        ]
        
        logger.info(f"JobEventConsumer initialized for topics: {self.topics}")
    
    def connect(self):
        """Connect to Kafka"""
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
            logger.info(f"✓ Connected to Kafka at {self.bootstrap_servers}")
            logger.info(f"✓ Subscribed to topics: {self.topics}")
            return True
        except KafkaError as e:
            logger.error(f"Failed to connect to Kafka: {e}")
            return False
    
    def start_consuming(self):
        """Start consuming messages from Kafka in background thread"""
        if self.running:
            logger.warning("Consumer already running")
            return
        
        if not self.consumer:
            if not self.connect():
                logger.error("Cannot start consumer - connection failed")
                return
        
        self.running = True
        self.consumer_thread = threading.Thread(target=self._consume_loop, daemon=True)
        self.consumer_thread.start()
        logger.info("✓ Kafka consumer started in background thread")
    
    def _consume_loop(self):
        """Internal consumption loop"""
        logger.info("Starting Kafka consumption loop...")
        
        try:
            while self.running:
                # Poll for messages with timeout
                messages = self.consumer.poll(timeout_ms=1000)
                
                for topic_partition, records in messages.items():
                    for record in records:
                        try:
                            self._handle_message(record.topic, record.value)
                        except Exception as e:
                            logger.error(f"Error processing message from {record.topic}: {e}", exc_info=True)
        
        except Exception as e:
            logger.error(f"Fatal error in consume loop: {e}", exc_info=True)
        finally:
            logger.info("Kafka consume loop stopped")
    
    def _handle_message(self, topic: str, event: Dict):
        """Route message to appropriate handler"""
        event_type = event.get("eventType")
        
        logger.info(f"Received event: {event_type} from topic: {topic}")
        
        if "created" in topic or event_type == "JOB_CREATED":
            self.handle_job_created(event)
        elif "updated" in topic or event_type == "JOB_UPDATED":
            self.handle_job_updated(event)
        elif "deleted" in topic or event_type == "JOB_DELETED":
            self.handle_job_deleted(event)
        else:
            logger.warning(f"Unknown event type: {event_type}")
    
    def handle_job_created(self, event: Dict):
        """Handle job creation event"""
        try:
            job_id = event.get("jobId")
            job_data = event.get("data", {})
            
            logger.info(f"Processing JOB_CREATED for jobId: {job_id}")
            
            # Format job data to text
            job_text = self.job_formatter.format_job_as_text(job_data)
            logger.info(f"Formatted job text length: {len(job_text)}")
            
            # Generate embedding
            embedding = self.embedding_generator.encode_job(job_text)
            logger.info(f"Generated embedding shape: {embedding.shape}")
            
            # Add to FAISS index
            self.faiss_manager.add_job_with_embedding(job_id, embedding, job_data)
            
            logger.info(f"✓ Added job {job_id} to FAISS index")
            
        except Exception as e:
            logger.error(f"Error handling job created event: {e}", exc_info=True)
    
    def handle_job_updated(self, event: Dict):
        """Handle job update event"""
        try:
            job_id = event.get("jobId")
            job_data = event.get("data", {})
            
            logger.info(f"Processing JOB_UPDATED for jobId: {job_id}")
            
            # Format job data to text
            job_text = self.job_formatter.format_job_as_text(job_data)
            
            # Generate embedding
            embedding = self.embedding_generator.encode_job(job_text)
            
            # Update in FAISS index (remove old + add new)
            self.faiss_manager.update_job_with_embedding(job_id, embedding, job_data)
            
            logger.info(f"✓ Updated job {job_id} in FAISS index")
            
        except Exception as e:
            logger.error(f"Error handling job updated event: {e}", exc_info=True)
    
    def handle_job_deleted(self, event: Dict):
        """Handle job deletion event"""
        try:
            job_id = event.get("jobId")
            reason = event.get("reason", "Unknown")
            
            logger.info(f"Processing JOB_DELETED for jobId: {job_id}, reason: {reason}")
            
            # Remove from FAISS index
            self.faiss_manager.remove_job(job_id)
            
            logger.info(f"✓ Removed job {job_id} from FAISS index")
            
        except Exception as e:
            logger.error(f"Error handling job deleted event: {e}", exc_info=True)
    
    def stop(self):
        """Stop consuming"""
        logger.info("Stopping Kafka consumer...")
        self.running = False
        
        if self.consumer_thread:
            self.consumer_thread.join(timeout=5)
        
        if self.consumer:
            self.consumer.close()
        
        logger.info("✓ Kafka consumer stopped")
    
    def get_stats(self) -> Dict:
        """Get consumer statistics"""
        return {
            "running": self.running,
            "topics": self.topics,
            "group_id": self.group_id
        }
        """Handle job update event"""
        # TODO: Implement
        logger.debug("TODO: Handle job updated event")
    
    def handle_job_deleted(self, event: Dict):
        """Handle job deletion event"""
        # TODO: Implement
        logger.debug("TODO: Handle job deleted event")
