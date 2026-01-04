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
    
    def add_job(self, job_id: str, job_text: str, job_data: Dict):
        """
        Add a new job to the index
        
        Args:
            job_id: Unique job ID
            job_text: Formatted job text for embedding
            job_data: Job metadata
        """
        try:
            # Check if already exists
            if job_id in self.job_id_to_id:
                logger.warning(f"Job {job_id} already exists, updating instead")
                self.update_job(job_id, job_text, job_data)
                return
            
            # Generate embedding (done by consumer before calling)
            # For now, store text in metadata for later embedding
            internal_id = self.next_id
            self.next_id += 1
            
            # Store mappings and metadata
            self.id_to_job_id[internal_id] = job_id
            self.job_id_to_id[job_id] = internal_id
            self.job_metadata[job_id] = {
                "job_text": job_text,
                "job_data": job_data
            }
            
            logger.info(f"✓ Added job {job_id} (internal_id={internal_id})")
            logger.info(f"  Total jobs in index: {len(self.job_id_to_id)}")
            
        except Exception as e:
            logger.error(f"Error adding job {job_id}: {e}", exc_info=True)
            raise
    
    def add_job_with_embedding(self, job_id: str, embedding: np.ndarray, job_data: Dict):
        """
        Add job with pre-computed embedding
        
        Args:
            job_id: Unique job ID
            embedding: Pre-computed embedding vector
            job_data: Job metadata
        """
        try:
            if job_id in self.job_id_to_id:
                logger.warning(f"Job {job_id} already exists, updating instead")
                self.update_job_with_embedding(job_id, embedding, job_data)
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
            
            logger.debug(f"✓ Added job {job_id} with embedding (internal_id={internal_id})")
            
        except Exception as e:
            logger.error(f"Error adding job with embedding {job_id}: {e}", exc_info=True)
            raise
    
    def update_job(self, job_id: str, job_text: str, job_data: Dict):
        """
        Update an existing job (remove old, add new)
        
        Args:
            job_id: Job ID to update
            job_text: Updated job text
            job_data: Updated job metadata
        """
        try:
            if job_id not in self.job_id_to_id:
                logger.warning(f"Job {job_id} not found, adding as new")
                self.add_job(job_id, job_text, job_data)
                return
            
            # Remove old version
            self.remove_job(job_id)
            
            # Add updated version
            self.add_job(job_id, job_text, job_data)
            
            logger.info(f"✓ Updated job {job_id}")
            
        except Exception as e:
            logger.error(f"Error updating job {job_id}: {e}", exc_info=True)
            raise
    
    def update_job_with_embedding(self, job_id: str, embedding: np.ndarray, job_data: Dict):
        """Update job with pre-computed embedding"""
        try:
            if job_id not in self.job_id_to_id:
                self.add_job_with_embedding(job_id, embedding, job_data)
                return
            
            self.remove_job(job_id)
            self.add_job_with_embedding(job_id, embedding, job_data)
            
            logger.debug(f"✓ Updated job {job_id} with embedding")
            
        except Exception as e:
            logger.error(f"Error updating job with embedding {job_id}: {e}", exc_info=True)
            raise
    
    def remove_job(self, job_id: str):
        """
        Remove a job from the index
        
        Note: FAISS doesn't support efficient removal, so we mark as removed
        and rebuild index periodically
        
        Args:
            job_id: Job ID to remove
        """
        try:
            if job_id not in self.job_id_to_id:
                logger.warning(f"Job {job_id} not found in index")
                return
            
            internal_id = self.job_id_to_id[job_id]
            
            # Remove from mappings
            del self.job_id_to_id[job_id]
            del self.id_to_job_id[internal_id]
            del self.job_metadata[job_id]
            
            logger.info(f"✓ Removed job {job_id} (internal_id={internal_id})")
            logger.info(f"  Note: FAISS vector still in index, rebuild needed for cleanup")
            
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
    
    def save_index(self, path: Optional[str] = None):
        """
        Save FAISS index and metadata to disk
        
        Args:
            path: Path to save index (uses self.index_path if not provided)
        """
        try:
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
            
            logger.info(f"✓ Loaded index from {path}")
            logger.info(f"  - Total jobs: {len(self.job_id_to_id)}")
            logger.info(f"  - Vectors in index: {self.index.ntotal}")
            
        except Exception as e:
            logger.error(f"Error loading index: {e}", exc_info=True)
            raise
    
    def get_stats(self) -> Dict:
        """Get index statistics"""
        return {
            "total_jobs": len(self.job_id_to_id),
            "vectors_in_index": self.index.ntotal,
            "dimension": self.dimension,
            "next_id": self.next_id
        }
