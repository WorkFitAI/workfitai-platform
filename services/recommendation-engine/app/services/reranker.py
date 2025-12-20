"""
Cross-encoder reranker for job recommendations
Uses trained cross-encoder model to rerank bi-encoder results
"""

import logging
import numpy as np
from typing import List, Dict, Tuple
from sentence_transformers import CrossEncoder

logger = logging.getLogger(__name__)


class JobReranker:
    """
    Rerank job recommendations using cross-encoder
    
    Pipeline:
    1. Bi-encoder retrieves top-K candidates (fast, ~50-100 jobs)
    2. Cross-encoder reranks to top-N (accurate, ~10-20 jobs)
    """
    
    def __init__(self, model_path: str):
        """
        Initialize cross-encoder model
        
        Args:
            model_path: Path to trained cross-encoder model
        """
        logger.info(f"Loading cross-encoder from: {model_path}")
        
        try:
            self.model = CrossEncoder(model_path)
            logger.info(f"✓ Cross-encoder loaded successfully")
            logger.info(f"  - Model: {model_path}")
            
        except Exception as e:
            logger.error(f"Failed to load cross-encoder: {e}")
            raise
    
    def rerank(
        self,
        resume_text: str,
        candidates: List[Dict],
        top_n: int = 20
    ) -> List[Dict]:
        """
        Rerank candidate jobs using cross-encoder
        
        Args:
            resume_text: Resume/profile text
            candidates: List of candidate jobs from bi-encoder
                       Each dict should have: {jobId, title, description, score, ...}
            top_n: Number of top results to return
            
        Returns:
            Reranked list of jobs with updated scores
        """
        if not candidates:
            logger.warn("No candidates to rerank")
            return []
        
        logger.info(f"Reranking {len(candidates)} candidates to top-{top_n}")
        
        # Prepare pairs for cross-encoder
        pairs = []
        for candidate in candidates:
            # Format job text (same as bi-encoder)
            job_text = self._format_job_text(candidate)
            pairs.append([resume_text, job_text])
        
        # Get cross-encoder scores (logits)
        try:
            scores = self.model.predict(pairs, convert_to_numpy=True, show_progress_bar=False)
            
            # Apply sigmoid to convert logits to probabilities
            scores = 1 / (1 + np.exp(-scores))  # Sigmoid
            
        except Exception as e:
            logger.error(f"Cross-encoder prediction failed: {e}")
            # Fallback to bi-encoder scores
            return candidates[:top_n]
        
        # Add cross-encoder scores to candidates
        for candidate, score in zip(candidates, scores):
            candidate['biEncoderScore'] = candidate.get('score', 0.0)  # Save original
            candidate['crossEncoderScore'] = float(score)
            candidate['score'] = float(score)  # Use cross-encoder as primary score
        
        # Sort by cross-encoder score and take top-N
        reranked = sorted(candidates, key=lambda x: x['crossEncoderScore'], reverse=True)
        reranked = reranked[:top_n]
        
        # Update ranks
        for idx, candidate in enumerate(reranked, 1):
            candidate['rank'] = idx
        
        logger.info(f"✓ Reranked to top-{len(reranked)} jobs")
        logger.info(f"  Top score: {reranked[0]['crossEncoderScore']:.4f}")
        logger.info(f"  Bi-encoder → Cross-encoder score change: "
                   f"{reranked[0]['biEncoderScore']:.4f} → {reranked[0]['crossEncoderScore']:.4f}")
        
        return reranked
    
    def _format_job_text(self, job: Dict) -> str:
        """
        Format job for cross-encoder input (same format as training)
        
        Args:
            job: Job dictionary with metadata
            
        Returns:
            Formatted job text
        """
        parts = []
        
        # Title (most important)
        if job.get('title'):
            parts.append(f"Job Title: {job['title']}")
        
        # Description
        if job.get('description'):
            desc = job['description'][:500]  # Limit length
            parts.append(f"Description: {desc}")
        
        # Company
        if job.get('company'):
            parts.append(f"Company: {job['company']}")
        
        # Location
        if job.get('location'):
            parts.append(f"Location: {job['location']}")
        
        # Experience level
        if job.get('experienceLevel'):
            parts.append(f"Experience: {job['experienceLevel']}")
        
        # Skills
        if job.get('skills') and isinstance(job['skills'], list):
            skills_str = ', '.join(job['skills'][:10])  # Top 10 skills
            parts.append(f"Required Skills: {skills_str}")
        
        # Salary
        if job.get('salary'):
            parts.append(f"Salary: {job['salary']}")
        
        return '\n'.join(parts)
    
    def get_model_info(self) -> Dict:
        """Get reranker model information"""
        return {
            "loaded": True,
            "type": "cross-encoder",
            "model": str(self.model)
        }
