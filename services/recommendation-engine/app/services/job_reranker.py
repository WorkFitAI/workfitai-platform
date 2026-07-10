"""
Cross-encoder reranker for job recommendations
Uses trained cross-encoder model to rerank bi-encoder results
"""

import logging
import numpy as np
from typing import List, Dict, Optional, Tuple
from sentence_transformers import CrossEncoder

from app.services.field_format import JOB_FIELDS, RESUME_FIELDS, build_fallback_text

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
        top_n: int = 20,
        resume_fields: Optional[Dict[str, str]] = None,
    ) -> List[Dict]:
        """
        Rerank candidate jobs using cross-encoder

        Args:
            resume_text: Resume/profile text (raw fallback field; always used
                as the resume_text field, see _format_resume_text)
            candidates: List of candidate jobs from bi-encoder. Each dict
                should have {jobId, title, score, ...} plus, when available,
                the raw job text fields (description, shortDescription,
                requirements, responsibilities, benefits) -- the caller is
                expected to merge these in from the FAISS search result
                metadata, since the trimmed JobRecommendation API shape alone
                doesn't carry them.
            top_n: Number of top results to return
            resume_fields: Optional structured CV fields (resume_summary/
                experience/skills/education) -- when supplied, used together
                with resume_text to build the same "[FIELD]"/"[FIELD: MISSING]"
                format the multi-field cross-encoder was trained on.

        Returns:
            Reranked list of jobs with updated scores
        """
        if not candidates:
            logger.warning("No candidates to rerank")
            return []

        logger.info(f"Reranking {len(candidates)} candidates to top-{top_n}")

        resume_ce_text = self._format_resume_text(resume_text, resume_fields)

        # Prepare pairs for cross-encoder
        pairs = []
        for candidate in candidates:
            # Format job text (same "[FIELD]" format the model was trained on)
            job_text = self._format_job_text(candidate)
            pairs.append([resume_ce_text, job_text])

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
    
    def _format_resume_text(self, resume_text: str, resume_fields: Optional[Dict[str, str]]) -> str:
        """
        Build resume-side cross-encoder input using the same "[FIELD]" /
        "[FIELD: MISSING]" section-header format cross-encoder-structured was
        trained on (job-recomendation/source/data_utils.py's build_fallback_text()).

        Falls back to the raw flat resume_text as the sole present field when
        no structured fields were supplied -- still correctly formatted (every
        structured section explicitly marked missing), not the old bare
        flat-string input.
        """
        fields = dict(resume_fields) if resume_fields else {}
        fields.setdefault("resume_text", resume_text)
        return build_fallback_text(fields, RESUME_FIELDS)

    def _format_job_text(self, job: Dict) -> str:
        """
        Build job-side cross-encoder input using the same "[FIELD]"/
        "[FIELD: MISSING]" format. `job` is the trimmed candidate dict
        (title/company-as-string/location/skills/score/...) with the raw text
        fields (description, shortDescription, requirements, responsibilities,
        benefits) merged in by the caller from the FAISS search result
        metadata -- built directly from this flat shape (not
        job_formatter.format_job_as_fields(), which expects the raw nested
        company dict job_metadata stores, not the flattened API shape here).

        Args:
            job: Job dictionary with metadata

        Returns:
            Formatted job text
        """
        overview_parts = []
        if job.get('title'):
            overview_parts.append(f"Job Title: {job['title']}")
        if job.get('company'):
            overview_parts.append(f"Company: {job['company']}")
        if job.get('location'):
            overview_parts.append(f"Location: {job['location']}")
        if job.get('skills') and isinstance(job['skills'], list):
            overview_parts.append(f"Skills: {', '.join(job['skills'][:10])}")
        full_text = job.get('description') or job.get('shortDescription') or ''
        if full_text:
            overview_parts.append(full_text[:1000])

        job_fields = {
            "job_description_text": "\n".join(overview_parts),
            "jd_overview": job.get('shortDescription', ''),
            "jd_requirements": job.get('requirements', ''),
            "jd_responsibilities": job.get('responsibilities', ''),
            "jd_preferred": job.get('benefits', ''),
        }
        return build_fallback_text(job_fields, JOB_FIELDS)

    def get_model_info(self) -> Dict:
        """Get reranker model information"""
        return {
            "loaded": True,
            "type": "cross-encoder",
            "model": str(self.model)
        }
