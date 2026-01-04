"""
Vault client for fetching configuration from HashiCorp Vault
"""

import os
import logging
import hvac
from typing import Dict, Optional, Any

logger = logging.getLogger(__name__)


class VaultClient:
    """
    HashiCorp Vault client for fetching secrets
    """
    
    def __init__(
        self, 
        vault_addr: Optional[str] = None,
        vault_token: Optional[str] = None,
        mount_point: str = "secret",
        service_name: str = "recommendation-engine"
    ):
        """
        Initialize Vault client
        
        Args:
            vault_addr: Vault server address (default: env VAULT_ADDR or http://vault:8200)
            vault_token: Vault authentication token (default: env VAULT_TOKEN)
            mount_point: KV secrets engine mount point (default: "secret")
            service_name: Service name for secrets path (default: "recommendation-engine")
        """
        self.vault_addr = vault_addr or os.getenv("VAULT_ADDR", "http://vault:8200")
        self.vault_token = vault_token or os.getenv("VAULT_TOKEN", "dev-token")
        self.mount_point = mount_point
        self.service_name = service_name
        
        self.client: Optional[hvac.Client] = None
        self._secrets_cache: Dict[str, Any] = {}
        
    def connect(self) -> bool:
        """
        Connect to Vault server
        
        Returns:
            True if connection successful, False otherwise
        """
        try:
            self.client = hvac.Client(
                url=self.vault_addr,
                token=self.vault_token
            )
            
            # Verify connection
            if not self.client.is_authenticated():
                logger.error("Vault authentication failed")
                return False
            
            logger.info(f"✓ Connected to Vault at {self.vault_addr}")
            return True
            
        except Exception as e:
            logger.error(f"Failed to connect to Vault: {e}", exc_info=True)
            return False
    
    def get_secrets(self, path: Optional[str] = None) -> Dict[str, Any]:
        """
        Get all secrets for the service
        
        Args:
            path: Custom secrets path (default: service_name)
            
        Returns:
            Dictionary of secrets
        """
        if not self.client:
            if not self.connect():
                logger.warning("Vault not connected, returning empty secrets")
                return {}
        
        secrets_path = path or self.service_name
        
        try:
            # Read from KV v2 engine
            response = self.client.secrets.kv.v2.read_secret_version(
                path=secrets_path,
                mount_point=self.mount_point
            )
            
            secrets = response.get("data", {}).get("data", {})
            self._secrets_cache = secrets
            
            logger.info(f"✓ Loaded {len(secrets)} secrets from Vault path: {secrets_path}")
            return secrets
            
        except hvac.exceptions.InvalidPath:
            logger.warning(f"Secrets path not found in Vault: {secrets_path}")
            return {}
        except Exception as e:
            logger.error(f"Failed to read secrets from Vault: {e}", exc_info=True)
            return {}
    
    def get_secret(self, key: str, default: Any = None) -> Any:
        """
        Get a single secret value
        
        Args:
            key: Secret key
            default: Default value if key not found
            
        Returns:
            Secret value or default
        """
        if not self._secrets_cache:
            self.get_secrets()
        
        return self._secrets_cache.get(key, default)
    
    def refresh_secrets(self) -> Dict[str, Any]:
        """
        Refresh secrets from Vault (bypass cache)
        
        Returns:
            Dictionary of secrets
        """
        logger.info("Refreshing secrets from Vault...")
        self._secrets_cache = {}
        return self.get_secrets()
    
    @property
    def is_connected(self) -> bool:
        """Check if Vault client is connected and authenticated"""
        return self.client is not None and self.client.is_authenticated()


def load_config_from_vault() -> Dict[str, Any]:
    """
    Load configuration from Vault and return as dictionary
    
    Returns:
        Dictionary of configuration values
    """
    vault_client = VaultClient()
    
    if not vault_client.connect():
        logger.warning("Failed to connect to Vault, using environment variables only")
        return {}
    
    secrets = vault_client.get_secrets()
    
    # Map Vault secrets to environment variable format
    config_mapping = {
        # Model Configuration
        "MODEL_PATH": secrets.get("model.path"),
        "MODEL_DIMENSION": secrets.get("model.dimension"),
        "BATCH_SIZE": secrets.get("batch.size"),
        
        # FAISS Configuration
        "FAISS_INDEX_PATH": secrets.get("faiss.index.path"),
        "FAISS_INDEX_TYPE": secrets.get("faiss.index.type"),
        "ENABLE_INDEX_PERSISTENCE": secrets.get("faiss.enable.persistence"),
        
        # Kafka Configuration
        "KAFKA_BOOTSTRAP_SERVERS": secrets.get("kafka.bootstrap.servers"),
        "KAFKA_CONSUMER_GROUP": secrets.get("kafka.consumer.group"),
        "KAFKA_TOPIC_JOB_CREATED": secrets.get("kafka.topic.job-created"),
        "KAFKA_TOPIC_JOB_UPDATED": secrets.get("kafka.topic.job-updated"),
        "KAFKA_TOPIC_JOB_DELETED": secrets.get("kafka.topic.job-deleted"),
        "KAFKA_AUTO_OFFSET_RESET": secrets.get("kafka.auto.offset.reset"),
        "ENABLE_KAFKA_CONSUMER": secrets.get("kafka.enable.consumer"),
        
        # Job Service Integration
        "JOB_SERVICE_URL": secrets.get("job.service.url"),
        "JOB_SERVICE_TIMEOUT": secrets.get("job.service.timeout"),
        
        # Resume Processing
        "MAX_RESUME_SIZE_MB": secrets.get("resume.max.size.mb"),
        
        # Search Configuration
        "DEFAULT_TOP_K": secrets.get("search.default.top-k"),
        "MAX_TOP_K": secrets.get("search.max.top-k"),
        "MIN_SIMILARITY_SCORE": secrets.get("search.min.similarity.score"),
        
        # Performance
        "ENABLE_CACHING": secrets.get("cache.enable"),
        "CACHE_TTL_SECONDS": secrets.get("cache.ttl.seconds"),
        "MAX_WORKERS": secrets.get("workers.max"),
        
        # Monitoring
        "ENABLE_METRICS": secrets.get("metrics.enable"),
        "METRICS_PORT": secrets.get("metrics.port"),
        
        # Initial Sync & Rebuild
        "ENABLE_INITIAL_SYNC": secrets.get("sync.enable.initial"),
        "INITIAL_SYNC_BATCH_SIZE": secrets.get("sync.initial.batch.size"),
        "ENABLE_PERIODIC_REBUILD": secrets.get("rebuild.enable.periodic"),
        "REBUILD_INTERVAL_HOURS": secrets.get("rebuild.interval.hours"),
    }
    
    # Filter out None values
    config = {k: v for k, v in config_mapping.items() if v is not None}
    
    logger.info(f"✓ Loaded {len(config)} configuration values from Vault")
    return config
