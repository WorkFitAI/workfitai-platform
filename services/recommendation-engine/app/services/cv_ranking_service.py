"""
Thin service wrapper around CVRankingPipeline from src/inference/pipeline.py.

Responsibilities:
- Convert Pydantic request models to pipeline data classes
- Call pipeline.rank()
- Serialize RankResult back to response dict

No business logic lives here — that belongs in the pipeline itself.
"""

import logging
from typing import Dict, Optional

from src.inference.pipeline import (
    CVRankingPipeline,
    JobInput,
    ResumeInput,
    RankResult,
    load_config,
)
from app.models.cv_rank_requests import CvRankRequest

logger = logging.getLogger(__name__)


def build_pipeline(config_path: Optional[str] = None) -> CVRankingPipeline:
    """Load config and initialize the pipeline (called once at startup)."""
    cfg = load_config(config_path)
    return CVRankingPipeline(cfg)


def rank_resumes(
    pipeline: CVRankingPipeline,
    request: CvRankRequest,
    embeddings_by_index: Optional[Dict[int, object]] = None,
) -> dict:
    """
    Translate API request → pipeline inputs → serialized response dict.

    embeddings_by_index, when given, maps resume_index → precomputed bi-encoder
    embedding from CvReferStore's cache — resumes missing an entry just get
    encoded on the fly in stage 1, same as before this cache existed.

    Raises RuntimeError if pipeline is not ready (model weights missing).
    """
    job = JobInput(
        job_index=request.job.job_index,
        jd_overview=request.job.jd_overview,
        jd_requirements=request.job.jd_requirements,
        jd_responsibilities=request.job.jd_responsibilities,
        jd_preferred=request.job.jd_preferred,
        job_description_text=request.job.job_description_text,
    )

    resumes = [
        ResumeInput(
            resume_index=cv.resume_index,
            resume_summary=cv.resume_summary,
            resume_experience=cv.resume_experience,
            resume_skills=cv.resume_skills,
            resume_education=cv.resume_education,
            resume_text=cv.resume_text,
        )
        for cv in request.resumes
    ]

    resume_embeddings = None
    if embeddings_by_index:
        resume_embeddings = [embeddings_by_index.get(r.resume_index) for r in resumes]

    opts = request.options
    result: RankResult = pipeline.rank(
        job=job,
        resumes=resumes,
        top_k=opts.top_k,
        top_n=opts.top_n,
        min_score=opts.min_score,
        good_fit_threshold=opts.good_fit_threshold,
        resume_embeddings=resume_embeddings,
    )

    return pipeline.to_dict(result)
