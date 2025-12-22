"""
Configuration management for Recommendation Engine
Loads settings from environment variables and Vault
"""

import os
import logging
from typing import Optional
from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import Field

# Setup logging before importing vault_client
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


# Load Vault configuration first (if enabled)
def load_vault_config():
    """Load configuration from Vault if enabled"""
    vault_enabled = os.getenv("VAULT_ENABLED", "true").lower() == "true"
    
    if not vault_enabled:
        logger.info("Vault integration disabled, using environment variables only")
        return {}
    
    try:
        from app.vault_client import load_config_from_vault
        vault_config = load_config_from_vault()
        
        # Set as environment variables (so Pydantic can read them)
        for key, value in vault_config.items():
            if value is not None and key not in os.environ:
                os.environ[key] = str(value)
        
        return vault_config
    except Exception as e:
        logger.warning(f"Failed to load Vault config: {e}. Using environment variables only.")
        return {}


# Load Vault config at module import time
_vault_config = load_vault_config()


class Settings(BaseSettings):
    """Application settings loaded from environment variables"""

    model_config = SettingsConfigDict(
        env_file=".env.local",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore"
    )
    
    # Service Info
    SERVICE_NAME: str = "recommendation-engine"
    VERSION: str = "1.0.0"
    ENVIRONMENT: str = Field(default="development", env="ENVIRONMENT")
    
    # Vault Configuration
    VAULT_ENABLED: bool = Field(default=True, env="VAULT_ENABLED")
    VAULT_ADDR: str = Field(default="http://vault:8200", env="VAULT_ADDR")
    VAULT_TOKEN: str = Field(default="dev-token", env="VAULT_TOKEN")
    
    # Server Configuration
    HOST: str = Field(default="0.0.0.0", env="HOST")
    PORT: int = Field(default=8000, env="PORT")
    LOG_LEVEL: str = Field(default="INFO", env="LOG_LEVEL")
    
    # Model Configuration
    MODEL_PATH: str = Field(default="/app/models/bi-encoder-e5-large", env="MODEL_PATH")
    MODEL_DIMENSION: int = Field(default=1024, env="MODEL_DIMENSION")
    BATCH_SIZE: int = Field(default=32, env="BATCH_SIZE")
    
    # Cross-Encoder Reranking
    ENABLE_RERANKING: bool = Field(default=True, env="ENABLE_RERANKING")
    CROSS_ENCODER_PATH: str = Field(default="/app/models/cross-encoder", env="CROSS_ENCODER_PATH")
    RERANK_TOP_K: int = Field(default=50, env="RERANK_TOP_K")  # Retrieve top-K from bi-encoder
    RERANK_TOP_N: int = Field(default=20, env="RERANK_TOP_N")  # Return top-N after reranking
    
    # FAISS Configuration
    FAISS_INDEX_PATH: str = Field(default="/app/data/faiss_index", env="FAISS_INDEX_PATH")
    FAISS_INDEX_TYPE: str = Field(default="IndexFlatIP", env="FAISS_INDEX_TYPE")
    ENABLE_INDEX_PERSISTENCE: bool = Field(default=True, env="ENABLE_INDEX_PERSISTENCE")
    
    # Kafka Configuration
    KAFKA_BOOTSTRAP_SERVERS: str = Field(default="kafka:29092", env="KAFKA_BOOTSTRAP_SERVERS")
    KAFKA_CONSUMER_GROUP: str = Field(default="recommendation-engine", env="KAFKA_CONSUMER_GROUP")
    KAFKA_TOPIC_JOB_CREATED: str = Field(default="job.created", env="KAFKA_TOPIC_JOB_CREATED")
    KAFKA_TOPIC_JOB_UPDATED: str = Field(default="job.updated", env="KAFKA_TOPIC_JOB_UPDATED")
    KAFKA_TOPIC_JOB_DELETED: str = Field(default="job.deleted", env="KAFKA_TOPIC_JOB_DELETED")
    KAFKA_AUTO_OFFSET_RESET: str = Field(default="earliest", env="KAFKA_AUTO_OFFSET_RESET")
    ENABLE_KAFKA_CONSUMER: bool = Field(default=True, env="ENABLE_KAFKA_CONSUMER")
    
    # Job Service Integration
    JOB_SERVICE_URL: str = Field(default="http://job-service:9082", env="JOB_SERVICE_URL")
    JOB_SERVICE_TIMEOUT: int = Field(default=30, env="JOB_SERVICE_TIMEOUT")
    
    # Resume Processing
    MAX_RESUME_SIZE_MB: int = Field(default=5, env="MAX_RESUME_SIZE_MB")
    SUPPORTED_RESUME_FORMATS: list[str] = Field(default=["pdf"], env="SUPPORTED_RESUME_FORMATS")
    
    # Search Configuration
    DEFAULT_TOP_K: int = Field(default=20, env="DEFAULT_TOP_K")
    MAX_TOP_K: int = Field(default=100, env="MAX_TOP_K")
    MIN_SIMILARITY_SCORE: float = Field(default=0.0, env="MIN_SIMILARITY_SCORE")
    
    # Performance
    ENABLE_CACHING: bool = Field(default=True, env="ENABLE_CACHING")
    CACHE_TTL_SECONDS: int = Field(default=3600, env="CACHE_TTL_SECONDS")
    MAX_WORKERS: int = Field(default=4, env="MAX_WORKERS")
    
    # Monitoring
    ENABLE_METRICS: bool = Field(default=True, env="ENABLE_METRICS")
    METRICS_PORT: int = Field(default=9090, env="METRICS_PORT")
    
    # Initial Sync
    ENABLE_INITIAL_SYNC: bool = Field(default=True, env="ENABLE_INITIAL_SYNC")
    INITIAL_SYNC_BATCH_SIZE: int = Field(default=50, env="INITIAL_SYNC_BATCH_SIZE")
    
    # Periodic Rebuild
    ENABLE_PERIODIC_REBUILD: bool = Field(default=False, env="ENABLE_PERIODIC_REBUILD")
    REBUILD_INTERVAL_HOURS: int = Field(default=24, env="REBUILD_INTERVAL_HOURS")
    
    def get_kafka_topics(self) -> list[str]:
        """Get list of Kafka topics to subscribe to"""
        return [
            self.KAFKA_TOPIC_JOB_CREATED,
            self.KAFKA_TOPIC_JOB_UPDATED,
            self.KAFKA_TOPIC_JOB_DELETED
        ]
    
    @property
    def max_resume_size_bytes(self) -> int:
        """Convert MB to bytes"""
        return self.MAX_RESUME_SIZE_MB * 1024 * 1024
    
    def is_production(self) -> bool:
        """Check if running in production"""
        return self.ENVIRONMENT.lower() == "production"


# Global settings instance
settings = Settings()


def get_settings() -> Settings:
    """Get application settings"""
    return settings
