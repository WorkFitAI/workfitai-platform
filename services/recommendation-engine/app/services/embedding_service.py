"""
Embedding generation using Sentence Transformers (E5-Large)
"""

import logging
import numpy as np
from typing import List, Union
from sentence_transformers import SentenceTransformer

logger = logging.getLogger(__name__)


class EmbeddingGenerator:
    """
    Generate embeddings using E5-Large model
    
    E5 models require specific prefixes for queries vs documents:
    - Queries: "query: {text}"
    - Documents: "passage: {text}"
    """
    
    def __init__(self, model_path: str):
        """
        Initialize Sentence Transformer model
        
        Args:
            model_path: Path to model directory or model name
        """
        logger.info(f"Loading Sentence Transformer model from: {model_path}")
        
        try:
            self.model = SentenceTransformer(model_path)
            self.dimension = self.model.get_sentence_embedding_dimension()
            
            logger.info(f"✓ Model loaded successfully")
            logger.info(f"  - Model: {model_path}")
            logger.info(f"  - Embedding dimension: {self.dimension}")
            logger.info(f"  - Max sequence length: {self.model.max_seq_length}")
            
        except Exception as e:
            logger.error(f"Failed to load model: {e}")
            raise
    
    def encode_job(self, job_text: str) -> np.ndarray:
        """
        Generate embedding for job description (document/passage)
        
        Args:
            job_text: Job description text
            
        Returns:
            Embedding vector (1024-dim for E5-Large)
        """
        # E5 models use "passage:" prefix for documents
        prefixed_text = f"passage: {job_text}"
        
        embedding = self.model.encode(
            prefixed_text,
            normalize_embeddings=True,  # Important for cosine similarity
            convert_to_numpy=True,
            show_progress_bar=False
        )
        
        return embedding.astype(np.float32)
    
    def encode_resume(self, resume_text: str) -> np.ndarray:
        """
        Generate embedding for resume/query
        
        Args:
            resume_text: Resume or query text
            
        Returns:
            Embedding vector (1024-dim)
        """
        # E5 models use "query:" prefix for queries
        prefixed_text = f"query: {resume_text}"
        
        embedding = self.model.encode(
            prefixed_text,
            normalize_embeddings=True,
            convert_to_numpy=True,
            show_progress_bar=False
        )
        
        return embedding.astype(np.float32)
    
    def encode_batch(
        self, 
        texts: List[str], 
        is_query: bool = False,
        batch_size: int = 32
    ) -> np.ndarray:
        """
        Batch encoding for efficiency
        
        Args:
            texts: List of texts to encode
            is_query: If True, use "query:" prefix; else use "passage:"
            batch_size: Batch size for encoding
            
        Returns:
            Array of embeddings (n_texts x dimension)
        """
        if not texts:
            return np.array([], dtype=np.float32).reshape(0, self.dimension)
        
        # Add appropriate prefix
        prefix = "query:" if is_query else "passage:"
        prefixed_texts = [f"{prefix} {text}" for text in texts]
        
        logger.debug(f"Encoding batch of {len(texts)} texts (is_query={is_query})")
        
        embeddings = self.model.encode(
            prefixed_texts,
            normalize_embeddings=True,
            convert_to_numpy=True,
            batch_size=batch_size,
            show_progress_bar=len(texts) > 100
        )
        
        return embeddings.astype(np.float32)
    
    def get_dimension(self) -> int:
        """Get embedding dimension"""
        return self.dimension
