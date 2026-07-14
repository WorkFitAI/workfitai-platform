"""
FAISS Index Manager for vector similarity search
"""

import logging
import numpy as np
import faiss
import pickle
import os
from typing import List, Dict, Tuple, Optional
from pathlib import Path

logger = logging.getLogger(__name__)


class FAISSIndexManager:
    """
    Manage FAISS index for job recommendations using cosine similarity
    """
    
    def __init__(self, dimension: int = 1024, index_path: Optional[str] = None):
        """
        Initialize FAISS index
        
        Args:
            dimension: Embedding dimension (1024 for E5-Large)
            index_path: Path to load/save index
        """
        self.dimension = dimension
        self.index_path = index_path
        
        # Initialize FAISS index (IndexFlatIP for cosine similarity with normalized vectors)
        self.index = faiss.IndexFlatIP(dimension)
        
        # Mapping between internal IDs and job IDs
        self.id_to_job_id: Dict[int, str] = {}  # internal_id -> job_id
        self.job_id_to_id: Dict[str, int] = {}  # job_id -> internal_id
        self.job_metadata: Dict[str, Dict] = {}  # job_id -> metadata
        self.next_id = 0

        # Per-field embeddings for the multi-field bi-encoder (e.g. jd_overview,
        # jd_requirements, ...), keyed by field name -> list aligned to
        # internal_id (same append-only, never-compacted-on-delete convention
        # as self.index / job_metadata). Empty when no caller has ever passed
        # field_embeddings/field_mask -- fully backward compatible with the
        # single flat-text embedding flow.
        self.field_presence: Dict[str, List[bool]] = {}
        self.field_embeddings: Dict[str, List[np.ndarray]] = {}
        
        logger.info(f"✓ FAISSIndexManager initialized")
        logger.info(f"  - Dimension: {dimension}")
        logger.info(f"  - Index type: IndexFlatIP (cosine similarity)")
        
        # Load existing index if path provided
        if index_path and os.path.exists(index_path):
            try:
                self.load_index(index_path)
            except Exception as e:
                logger.warning(f"Failed to load index from {index_path}: {e}")
                logger.info("Starting with empty index")
    
    def add_job_with_embedding(
        self,
        job_id: str,
        embedding: np.ndarray,
        job_data: Dict,
        field_embeddings: Optional[Dict[str, np.ndarray]] = None,
        field_mask: Optional[Dict[str, bool]] = None,
    ):
        """
        Add job with pre-computed embedding.

        Args:
            job_id: Unique job ID
            embedding: Pre-computed pooled embedding vector
            job_data: Job metadata
            field_embeddings: Optional per-field embeddings (present fields
                only), e.g. from EmbeddingGenerator.encode_job_fields(). Keyed
                by field name (jd_overview, jd_requirements, ...).
            field_mask: Optional per-field presence dict (True/False for
                every field name, including absent ones) -- required
                alongside field_embeddings to keep per-field storage row-
                aligned with the FAISS index across all jobs.
        """
        try:
            if job_id in self.job_id_to_id:
                logger.warning(f"Job {job_id} already exists, updating instead")
                self.update_job_with_embedding(job_id, embedding, job_data, field_embeddings, field_mask)
                return

            # Ensure embedding is 2D
            if embedding.ndim == 1:
                embedding = embedding.reshape(1, -1)

            # Add to FAISS index
            internal_id = self.next_id
            self.index.add(embedding)
            self.next_id += 1

            # Store mappings and metadata
            self.id_to_job_id[internal_id] = job_id
            self.job_id_to_id[job_id] = internal_id
            self.job_metadata[job_id] = job_data

            self._append_field_row(field_embeddings, field_mask)

            logger.debug(f"✓ Added job {job_id} with embedding (internal_id={internal_id})")

        except Exception as e:
            logger.error(f"Error adding job with embedding {job_id}: {e}", exc_info=True)
            raise

    def _append_field_row(
        self,
        field_embeddings: Optional[Dict[str, np.ndarray]],
        field_mask: Optional[Dict[str, bool]],
    ) -> None:
        """
        Append one row -- matching the job just added at internal_id
        (self.next_id - 1) -- to every tracked per-field array.

        Keeps every self.field_presence[name]/self.field_embeddings[name]
        list exactly self.next_id long, so they stay row-aligned with
        self.index no matter which individual calls did or didn't supply
        field data: a field name seen for the first time gets backfilled
        with False/zero rows for every job added before it, and any field
        this particular call didn't mention gets a False/zero row too.
        """
        field_mask = field_mask or {}
        field_embeddings = field_embeddings or {}

        for name in field_mask:
            if name not in self.field_presence:
                backfill_len = self.next_id - 1  # rows already added, before this one
                self.field_presence[name] = [False] * backfill_len
                self.field_embeddings[name] = [np.zeros(self.dimension, dtype=np.float32)] * backfill_len

        for name in self.field_presence:
            present = bool(field_mask.get(name, False))
            self.field_presence[name].append(present)
            emb = field_embeddings.get(name) if present else None
            if emb is None:
                emb = np.zeros(self.dimension, dtype=np.float32)
            self.field_embeddings[name].append(np.asarray(emb, dtype=np.float32))

    def update_job_with_embedding(
        self,
        job_id: str,
        embedding: np.ndarray,
        job_data: Dict,
        field_embeddings: Optional[Dict[str, np.ndarray]] = None,
        field_mask: Optional[Dict[str, bool]] = None,
    ):
        """Update job with pre-computed embedding (same field_embeddings/
        field_mask contract as add_job_with_embedding)."""
        try:
            if job_id not in self.job_id_to_id:
                self.add_job_with_embedding(job_id, embedding, job_data, field_embeddings, field_mask)
                return

            self.remove_job(job_id)
            self.add_job_with_embedding(job_id, embedding, job_data, field_embeddings, field_mask)

            logger.debug(f"✓ Updated job {job_id} with embedding")

        except Exception as e:
            logger.error(f"Error updating job with embedding {job_id}: {e}", exc_info=True)
            raise
    
    def compact_index(self) -> int:
        """Rebuild the FAISS index to remove accumulated ghost vectors.

        Ghost vectors appear whenever remove_job() or update_job_with_embedding()
        is called — FAISS IndexFlatIP does not support in-place deletion, so the
        old vectors stay in the index while the mapping entries are removed.
        search() already skips ghosts correctly, but they waste memory and slow
        scans. compact_index() reconstructs the index from live vectors only.

        Returns the number of ghost vectors removed (0 if index was already clean).
        """
        ghost_count = self.index.ntotal - len(self.job_id_to_id)
        if ghost_count == 0:
            return 0

        new_index = faiss.IndexFlatIP(self.dimension)
        new_id_to_job_id: Dict[int, str] = {}
        new_job_id_to_id: Dict[str, int] = {}
        new_field_presence: Dict[str, List[bool]] = {k: [] for k in self.field_presence}
        new_field_embeddings: Dict[str, List] = {k: [] for k in self.field_embeddings}
        new_next_id = 0

        for old_id in range(self.next_id):
            if old_id not in self.id_to_job_id:
                continue  # ghost — skip
            job_id = self.id_to_job_id[old_id]
            embedding = self.index.reconstruct(old_id).reshape(1, -1)
            new_index.add(embedding)
            new_id_to_job_id[new_next_id] = job_id
            new_job_id_to_id[job_id] = new_next_id

            for name in new_field_presence:
                if old_id < len(self.field_presence[name]):
                    new_field_presence[name].append(self.field_presence[name][old_id])
                    new_field_embeddings[name].append(self.field_embeddings[name][old_id])
                else:
                    new_field_presence[name].append(False)
                    new_field_embeddings[name].append(np.zeros(self.dimension, dtype=np.float32))

            new_next_id += 1

        self.index = new_index
        self.id_to_job_id = new_id_to_job_id
        self.job_id_to_id = new_job_id_to_id
        self.field_presence = new_field_presence
        self.field_embeddings = new_field_embeddings
        self.next_id = new_next_id

        logger.info(
            "compact_index: removed %d ghost vector(s), %d live job(s) remain",
            ghost_count, len(self.job_id_to_id),
        )
        return ghost_count

    def remove_job(self, job_id: str):
        """
        Remove a job from the index.

        The FAISS vector becomes a ghost (not addressable, but still physically
        in the index). compact_index() is called automatically when the ghost
        ratio is high to prevent unbounded memory growth.
        """
        try:
            if job_id not in self.job_id_to_id:
                logger.warning(f"Job {job_id} not found in index")
                return

            internal_id = self.job_id_to_id[job_id]

            del self.job_id_to_id[job_id]
            del self.id_to_job_id[internal_id]
            del self.job_metadata[job_id]

            logger.info(f"✓ Removed job {job_id} (internal_id={internal_id})")

            # Auto-compact when ghost count is large relative to live jobs so the
            # index doesn't grow unboundedly under sustained update/delete load.
            live_count = len(self.job_id_to_id)
            ghost_count = self.index.ntotal - live_count
            if ghost_count >= max(50, live_count):
                self.compact_index()

        except Exception as e:
            logger.error(f"Error removing job {job_id}: {e}", exc_info=True)
            raise
    
    def search(
        self, 
        query_embedding: np.ndarray, 
        top_k: int = 20,
        filters: Optional[Dict] = None
    ) -> List[Dict]:
        """
        Search for similar jobs
        
        Args:
            query_embedding: Query embedding vector
            top_k: Number of results to return
            filters: Optional filters (location, salary, etc.)
            
        Returns:
            List of job matches with scores
        """
        try:
            if self.index.ntotal == 0:
                logger.warning("Index is empty")
                return []
            
            # Ensure embedding is 2D
            if query_embedding.ndim == 1:
                query_embedding = query_embedding.reshape(1, -1)
            
            # Search FAISS index
            scores, indices = self.index.search(query_embedding, min(top_k * 2, self.index.ntotal))
            
            # Convert to results
            results = []
            for score, idx in zip(scores[0], indices[0]):
                # Skip if not in mappings (removed jobs)
                if idx not in self.id_to_job_id:
                    continue
                
                job_id = self.id_to_job_id[idx]
                
                # Apply filters if provided
                if filters and not self._matches_filters(job_id, filters):
                    continue
                
                # Get job metadata
                metadata = self.job_metadata.get(job_id, {})
                
                # Flatten result
                result = {
                    "jobId": job_id,
                    "score": float(score),
                    **metadata  # Include all metadata fields at top level
                }
                
                results.append(result)
                
                if len(results) >= top_k:
                    break
            
            logger.debug(f"Found {len(results)} matches")
            return results
            
        except Exception as e:
            logger.error(f"Error searching index: {e}", exc_info=True)
            return []
    
    def _matches_filters(self, job_id: str, filters: Dict) -> bool:
        """Check if job matches filters"""
        metadata = self.job_metadata.get(job_id, {})
        
        # Location filter
        if "location" in filters:
            job_location = metadata.get("location", "")
            if filters["location"].lower() not in job_location.lower():
                return False
        
        # Salary filter
        if "minSalary" in filters:
            job_salary_max = metadata.get("salaryMax")
            if job_salary_max and job_salary_max < filters["minSalary"]:
                return False
        
        if "maxSalary" in filters:
            job_salary_min = metadata.get("salaryMin")
            if job_salary_min and job_salary_min > filters["maxSalary"]:
                return False
        
        # Experience level filter
        if "experienceLevel" in filters:
            job_exp = metadata.get("experienceLevel", "")
            if job_exp != filters["experienceLevel"]:
                return False
        
        # Job type filter
        if "jobType" in filters:
            job_type = metadata.get("jobType", "")
            if job_type != filters["jobType"]:
                return False
        
        return True
    
    def get_job_by_id(self, job_id: str) -> Optional[Dict]:
        """
        Get job data and embedding by ID
        
        Args:
            job_id: Job ID to retrieve
            
        Returns:
            Dict with job data, metadata, and embedding, or None if not found
        """
        try:
            if job_id not in self.job_id_to_id:
                logger.warning(f"Job {job_id} not found in index")
                return None
            
            internal_id = self.job_id_to_id[job_id]
            
            # Get embedding from FAISS
            embedding = self.index.reconstruct(internal_id)
            
            # Get metadata
            metadata = self.job_metadata.get(job_id, {})
            
            return {
                "jobId": job_id,
                "embedding": embedding,
                "metadata": metadata,
                **metadata  # Flatten job_data fields
            }
            
        except Exception as e:
            logger.error(f"Error getting job {job_id}: {e}", exc_info=True)
            return None

    def get_job_field_embeddings(self, job_id: str) -> Optional[Dict[str, np.ndarray]]:
        """
        Per-field embeddings for a job's PRESENT fields only (fields with a
        False presence mask are omitted, not returned as zero vectors) --
        used by the API layer to compute a per-field similarity breakdown
        against a query's per-field embeddings.

        Returns None if the job isn't indexed, or {} if it's indexed but no
        field data was ever stored for it (e.g. added via the flat-embedding
        path only).
        """
        if job_id not in self.job_id_to_id:
            return None

        internal_id = self.job_id_to_id[job_id]
        result: Dict[str, np.ndarray] = {}
        for name, presence in self.field_presence.items():
            if internal_id < len(presence) and presence[internal_id]:
                result[name] = self.field_embeddings[name][internal_id]
        return result

    def save_index(self, path: Optional[str] = None):
        """
        Save FAISS index and metadata to disk.

        Compacts ghost vectors before writing so the on-disk index is always clean.
        """
        try:
            self.compact_index()
            save_path = path or self.index_path
            if not save_path:
                logger.warning("No save path provided")
                return
            
            # Create directory if needed
            Path(save_path).parent.mkdir(parents=True, exist_ok=True)
            
            # Save FAISS index
            faiss.write_index(self.index, save_path)
            
            # Save metadata
            metadata_path = save_path + ".metadata"
            with open(metadata_path, 'wb') as f:
                pickle.dump({
                    "id_to_job_id": self.id_to_job_id,
                    "job_id_to_id": self.job_id_to_id,
                    "job_metadata": self.job_metadata,
                    "next_id": self.next_id
                }, f)

            # Save per-field embedding sidecar (multi-field bi-encoder), same
            # naming convention as the .metadata pickle above.
            fields_path = save_path + ".fields.npz"
            if self.field_presence:
                npz_payload = {}
                for name, presence in self.field_presence.items():
                    npz_payload[f"{name}__presence"] = np.array(presence, dtype=bool)
                    embeddings = self.field_embeddings[name]
                    npz_payload[f"{name}__embeddings"] = (
                        np.stack(embeddings) if embeddings else np.zeros((0, self.dimension), dtype=np.float32)
                    )
                np.savez(fields_path, **npz_payload)
                logger.info(f"✓ Saved per-field embeddings to {fields_path} ({len(self.field_presence)} fields)")

            logger.info(f"✓ Saved index to {save_path}")
            logger.info(f"  - Total jobs: {len(self.job_id_to_id)}")

        except Exception as e:
            logger.error(f"Error saving index: {e}", exc_info=True)
            raise
    
    def load_index(self, path: str):
        """
        Load FAISS index and metadata from disk
        
        Args:
            path: Path to load index from
        """
        try:
            # Load FAISS index
            self.index = faiss.read_index(path)
            
            # Load metadata
            metadata_path = path + ".metadata"
            with open(metadata_path, 'rb') as f:
                data = pickle.load(f)
                self.id_to_job_id = data["id_to_job_id"]
                self.job_id_to_id = data["job_id_to_id"]
                self.job_metadata = data["job_metadata"]
                self.next_id = data["next_id"]

            # Load per-field embedding sidecar, if present (older indexes
            # saved before multi-field support won't have one).
            self.field_presence = {}
            self.field_embeddings = {}
            fields_path = path + ".fields.npz"
            if os.path.exists(fields_path):
                field_data = np.load(fields_path)
                field_names = sorted({key.rsplit("__", 1)[0] for key in field_data.files})
                for name in field_names:
                    self.field_presence[name] = field_data[f"{name}__presence"].tolist()
                    self.field_embeddings[name] = list(field_data[f"{name}__embeddings"])
                logger.info(f"✓ Loaded per-field embeddings from {fields_path} ({len(field_names)} fields)")

            logger.info(f"✓ Loaded index from {path}")
            logger.info(f"  - Total jobs: {len(self.job_id_to_id)}")
            logger.info(f"  - Vectors in index: {self.index.ntotal}")

        except Exception as e:
            logger.error(f"Error loading index: {e}", exc_info=True)
            raise
    
    def get_stats(self) -> Dict:
        """Get index statistics"""
        live = len(self.job_id_to_id)
        return {
            "total_jobs": live,
            "vectors_in_index": self.index.ntotal,
            "ghost_vectors": self.index.ntotal - live,
            "dimension": self.dimension,
            "next_id": self.next_id,
        }
