"""
CV Ranking API routes — POST /api/v1/cv-ranking/rank

Used by HR to rank candidate CVs from job applications.
Delegates to CVRankingPipeline via cv_ranking_service.
"""

import logging
from fastapi import APIRouter, HTTPException, Request, status

from app.models.cv_rank_requests import CvRankRequest
from app.models.cv_rank_responses import CvRankResponse, RankedResumeResponse
from app.services.cv_ranking_service import rank_resumes

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/cv-ranking", tags=["CV Ranking"])


@router.post("/rank", response_model=CvRankResponse)
async def rank_cvs(request: CvRankRequest, req: Request):
    """
    Rank candidate CVs for a job posting.

    Three-stage pipeline:
    1. Bi-Encoder + FAISS — retrieves top-K candidates
    2. Cross-Encoder — reranks to top-N with higher accuracy
    3. T5 Explanation — generates natural language rationale per CV

    Returns CVs with score >= min_score, sorted by score descending.
    CVs below min_score are excluded entirely.
    """
    pipeline = req.app.state.cv_ranking_pipeline()

    if pipeline is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="CV ranking pipeline is not available. Model weights have not been loaded yet.",
        )

    if not pipeline.is_ready:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=(
                "CV ranking pipeline is initializing or model weights are missing. "
                "Place trained models in the cv-refer models directory."
            ),
        )

    logger.info(
        "CV rank request: job_index=%s, candidates=%d, options=%s",
        request.job.job_index,
        len(request.resumes),
        request.options.model_dump(exclude_none=True),
    )

    try:
        result = rank_resumes(pipeline, request)
    except RuntimeError as e:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(e))
    except Exception as e:
        logger.error("CV ranking failed: %s", e, exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(e)
        )

    logger.info(
        "CV rank complete: job_index=%s, ranked=%d/%d, time=%.1fms",
        result["job_index"],
        len(result["ranked_resumes"]),
        result["total_candidates"],
        result["processing_time_ms"],
    )

    return CvRankResponse(
        job_index=result["job_index"],
        job_overview=result["job_overview"],
        total_candidates=result["total_candidates"],
        processing_time_ms=result["processing_time_ms"],
        ranked_resumes=[RankedResumeResponse(**r) for r in result["ranked_resumes"]],
    )
