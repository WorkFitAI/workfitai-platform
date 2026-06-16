"""
CV Ranking API routes.

POST /api/v1/cv-ranking/rank          — raw request (caller supplies job + resumes)
POST /api/v1/cv-ranking/rank-by-job/{job_id} — HR button: uses Kafka-synced store
"""

import logging
import time
from typing import Optional

import anyio
from fastapi import APIRouter, Body, HTTPException, Request, status

from app.models.cv_rank_requests import CvRankRequest, OptionsRequest
from app.models.cv_rank_responses import (
    CvRankByJobResponse,
    CvRankResponse,
    RankedApplicantResponse,
    RankedResumeResponse,
)
from app.services.cv_rank_assembly import build_cv_rank_request, fetch_job_data
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
        result = await anyio.to_thread.run_sync(lambda: rank_resumes(pipeline, request))
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


# ---------------------------------------------------------------------------
# HR button: rank candidates already synced via Kafka
# ---------------------------------------------------------------------------

@router.post("/rank-by-job/{job_id}", response_model=CvRankByJobResponse)
async def rank_by_job(
    job_id: str,
    req: Request,
    options: Optional[OptionsRequest] = Body(default=None),
):
    """
    Rank all active applicants for a job using pre-synced Kafka data.

    - Applicant list maintained by CvReferConsumer (application-events)
    - CV data maintained by CvReferConsumer (cv.updated)
    - Job data fetched once from job-service

    Returns 404 when job has no applicants in the ranking pool.
    Returns 503 when pipeline is not ready or CV data is missing for applicants.
    """
    from app.config import get_settings
    from app.services.cv_ranking_cache import get_cv_ranking_cache

    start_time = time.time()
    settings = get_settings()
    pipeline = req.app.state.cv_ranking_pipeline()
    store = req.app.state.cv_refer_store()

    if pipeline is None or not pipeline.is_ready:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="CV ranking pipeline is not ready. Model weights are missing.",
        )

    # 404 guard — always check first (pool state is in-memory, O(1))
    usernames = store.get_applicant_usernames(job_id)
    if not usernames:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"No active applicants in ranking pool for job {job_id}.",
        )

    # -------------------------------------------------------------------
    # Cache fast-path: check BEFORE any I/O.
    # Fresh or stale-while-revalidate entries are returned immediately —
    # no HTTP call to job-service, no store rebuild.
    # -------------------------------------------------------------------
    cache = None
    cooldown = 0.0
    if settings.CV_RANKING_CACHE_ENABLED:
        cache = get_cv_ranking_cache()
        cooldown = float(settings.CV_RANKING_CACHE_COOLDOWN_SECONDS)
        cached_payload, cache_hit = cache.try_hit(job_id, cooldown)
        if cache_hit:
            logger.info(
                "rank-by-job %s: cache=hit ranked=%d time=%.1fms",
                job_id, len(cached_payload["ranked_applicants"]),
                (time.time() - start_time) * 1000,
            )
            return CvRankByJobResponse(
                job_id=job_id,
                job_overview=cached_payload["job_overview"],
                total_candidates=cached_payload["total_candidates"],
                ranked_count=len(cached_payload["ranked_applicants"]),
                processing_time_ms=(time.time() - start_time) * 1000,
                ranked_applicants=cached_payload["ranked_applicants"],
            )

    # Cache miss (or disabled): fetch job data and run full pipeline
    job_data = await fetch_job_data(settings.JOB_SERVICE_URL, job_id, settings.JOB_SERVICE_TIMEOUT)
    if job_data is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"Failed to fetch job details for job {job_id} from job-service.",
        )

    cv_rank_request, idx_to_username, reason = build_cv_rank_request(job_id, store, job_data, options)
    if cv_rank_request is None:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=reason)

    logger.info(
        "rank-by-job %s: %d applicants in pool, %d with CV data",
        job_id, len(usernames), len(idx_to_username),
    )

    # Payload stored: dict with job_overview, total_candidates, ranked_applicants
    # ranked_applicants has usernames already resolved — avoids idx staleness risk.
    def _compute() -> dict:
        result = rank_resumes(pipeline, cv_rank_request)
        applicants = [
            RankedApplicantResponse(
                username=idx_to_username.get(r["resume_index"], "unknown"),
                score=r["score"],
                similarity_score=r["similarity_score"],
                cross_score=r["cross_score"],
                label=r["label"],
                explanation=r["explanation"],
            )
            for r in result["ranked_resumes"]
        ]
        return {
            "job_overview": result["job_overview"],
            "total_candidates": result["total_candidates"],
            "ranked_applicants": applicants,
        }

    if cache is not None:
        try:
            payload, _ = await anyio.to_thread.run_sync(
                lambda: cache.get_or_compute(job_id, _compute, cooldown)
            )
        except RuntimeError as exc:
            raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exc))
        except Exception as exc:
            logger.error("rank-by-job %s cache/compute failed: %s", job_id, exc, exc_info=True)
            raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc))
        logger.info(
            "rank-by-job %s: cache=miss ranked=%d time=%.1fms",
            job_id, len(payload["ranked_applicants"]),
            (time.time() - start_time) * 1000,
        )
    else:
        try:
            payload = await anyio.to_thread.run_sync(_compute)
        except RuntimeError as exc:
            raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exc))
        except Exception as exc:
            logger.error("rank-by-job %s failed: %s", job_id, exc, exc_info=True)
            raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(exc))
        logger.info(
            "rank-by-job %s: cache=disabled ranked=%d time=%.1fms",
            job_id, len(payload["ranked_applicants"]), (time.time() - start_time) * 1000,
        )

    return CvRankByJobResponse(
        job_id=job_id,
        job_overview=payload["job_overview"],
        total_candidates=payload["total_candidates"],
        ranked_count=len(payload["ranked_applicants"]),
        processing_time_ms=(time.time() - start_time) * 1000,
        ranked_applicants=payload["ranked_applicants"],
    )
