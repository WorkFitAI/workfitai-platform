"""
Embedding generation using Sentence Transformers (E5-Large)
"""

import logging
import numpy as np
from typing import Dict, List, Optional, Tuple, Union
from sentence_transformers import SentenceTransformer

from app.services.field_format import field_present

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

    # ------------------------------------------------------------------ #
    # Multi-field encoding (bi-encoder-e5-large-multifield)               #
    # ------------------------------------------------------------------ #
    #
    # Encodes each present field separately, then masked-mean-pools the RAW
    # (non-normalized) per-field embeddings and L2-normalizes only the final
    # pooled vector -- this matches how the model was trained
    # (job-recomendation/source/multifield_encoder.py's MultiFieldEncoder:
    # per-field pooling happens on raw sentence embeddings, normalization is
    # applied once, only when comparing the final pooled vectors). Encoding
    # per field first and normalizing first would silently produce a
    # different vector than what the model was trained/evaluated with.

    def encode_job_fields(
        self, fields: Dict[str, Optional[str]], field_order: List[str]
    ) -> Tuple[np.ndarray, Dict[str, np.ndarray], List[bool]]:
        """Encode job-side structured fields. Returns (pooled_embedding,
        per_field_embeddings, presence_mask) -- presence_mask is aligned to
        field_order; per_field_embeddings is keyed by field name and holds
        RAW (non-normalized) embeddings for present fields only."""
        return self._encode_fields(fields, field_order, prefix="passage: ")

    def encode_resume_fields(
        self, fields: Dict[str, Optional[str]], field_order: List[str]
    ) -> Tuple[np.ndarray, Dict[str, np.ndarray], List[bool]]:
        """Encode resume-side structured fields. Same contract as
        encode_job_fields, using the "query: " e5 prefix."""
        return self._encode_fields(fields, field_order, prefix="query: ")

    def _encode_fields(
        self, fields: Dict[str, Optional[str]], field_order: List[str], prefix: str
    ) -> Tuple[np.ndarray, Dict[str, np.ndarray], List[bool]]:
        present_texts: List[str] = []
        present_names: List[str] = []
        mask: List[bool] = []

        for name in field_order:
            value = fields.get(name)
            if field_present(value):
                present_texts.append(f"{prefix}{value}")
                present_names.append(name)
                mask.append(True)
            else:
                mask.append(False)

        if not present_texts:
            logger.warning("No present fields to encode (field_order=%s) -- returning zero vector", field_order)
            return np.zeros(self.dimension, dtype=np.float32), {}, mask

        raw_embeddings = self.model.encode(
            present_texts,
            normalize_embeddings=False,
            convert_to_numpy=True,
            show_progress_bar=False,
        ).astype(np.float32)
        if raw_embeddings.ndim == 1:
            raw_embeddings = raw_embeddings.reshape(1, -1)

        per_field_embeddings = {name: emb for name, emb in zip(present_names, raw_embeddings)}

        pooled_raw = raw_embeddings.mean(axis=0)
        norm = np.linalg.norm(pooled_raw) + 1e-8
        pooled = (pooled_raw / norm).astype(np.float32)

        return pooled, per_field_embeddings, mask
