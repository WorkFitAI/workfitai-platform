"""
CV Ranking API routes.

POST /api/v1/cv-ranking/rank          — raw request (caller supplies job + resumes)
POST /api/v1/cv-ranking/rank-by-job/{job_id} — HR button: uses Kafka-synced store
"""

import asyncio
import logging
import time
from typing import Optional

import anyio
from fastapi import APIRouter, Body, HTTPException, Request, status
from fastapi.responses import JSONResponse

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


def _ensure_cv_referral_enabled(req: Request) -> None:
    """
    Raise 503 if cv-referral AI is disabled by the admin platform-wide toggle.

    HR-initiated feature (HR browsing AI-ranked candidates) - no candidate
    consent dimension, admin toggle is the only gate.
    """
    store = req.app.state.feature_toggle_store()
    if store is not None and not store.get("cv-referral"):
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="AI CV ranking is currently disabled by administrator.",
        )


async def _background_rank_by_job(job_id, store, settings, pipeline, cache, options) -> None:
    """
    Off-request-path compute for a rank-by-job cache miss.

    Always calls cache.finish_compute() exactly once (with the result, or None on
    failure) to release the single-flight slot claimed by try_start_compute() — a
    later request for the same job_id must be able to retry, not wait forever.
    """
    result = None
    try:
        job_data = await fetch_job_data(settings.JOB_SERVICE_URL, job_id, settings.JOB_SERVICE_TIMEOUT)
        if job_data is None:
            logger.warning("rank-by-job %s: background compute aborted — job-service fetch failed", job_id)
            return

        cv_rank_request, idx_to_username, reason, embeddings_by_index = build_cv_rank_request(
            job_id, store, job_data, options
        )
        if cv_rank_request is None:
            logger.warning("rank-by-job %s: background compute aborted — %s", job_id, reason)
            return

        def _compute() -> dict:
            pipeline_result = rank_resumes(pipeline, cv_rank_request, embeddings_by_index)
            applicants = [
                RankedApplicantResponse(
                    username=idx_to_username.get(r["resume_index"], "unknown"),
                    score=r["score"],
                    similarity_score=r["similarity_score"],
                    cross_score=r["cross_score"],
                    label=r["label"],
                    explanation=r["explanation"],
                    match_points=r.get("match_points", []),
                    miss_points=r.get("miss_points", []),
                    input_coverage=r.get("input_coverage", 1.0),
                    fields_used=r.get("fields_used", {}),
                )
                for r in pipeline_result["ranked_resumes"]
            ]
            return {
                "job_overview": pipeline_result["job_overview"],
                "total_candidates": pipeline_result["total_candidates"],
                "ranked_applicants": applicants,
            }

        result = await anyio.to_thread.run_sync(_compute)
        logger.info(
            "rank-by-job %s: background compute stored ranked=%d",
            job_id, len(result["ranked_applicants"]),
        )
    except Exception as exc:
        logger.error("rank-by-job %s: background compute failed: %s", job_id, exc, exc_info=True)
    finally:
        cache.finish_compute(job_id, result)


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
    _ensure_cv_referral_enabled(req)

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

    _ensure_cv_referral_enabled(req)

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

    # -------------------------------------------------------------------
    # Cache miss: don't block the request on a 10-30s pipeline run. Claim the
    # single-flight slot (non-blocking) and kick off the compute in the
    # background; the caller gets a 202 + Retry-After immediately and is
    # expected to retry — the next call will be a cache hit once compute lands.
    # -------------------------------------------------------------------
    if cache is not None:
        claimed = cache.try_start_compute(job_id)
        if claimed:
            asyncio.create_task(
                _background_rank_by_job(job_id, store, settings, pipeline, cache, options)
            )
            logger.info("rank-by-job %s: cache=miss, started background compute", job_id)
        else:
            logger.info("rank-by-job %s: cache=miss, compute already in flight", job_id)

        retry_after = settings.CV_RANKING_MISS_RETRY_AFTER_SECONDS
        return JSONResponse(
            status_code=status.HTTP_202_ACCEPTED,
            headers={"Retry-After": str(retry_after)},
            content={
                "success": False,
                "status": "computing",
                "job_id": job_id,
                "job_overview": "",
                "total_candidates": 0,
                "ranked_count": 0,
                "processing_time_ms": 0.0,
                "ranked_applicants": [],
                "retry_after_seconds": retry_after,
                "message": f"Ranking for job {job_id} is being computed. Retry in {retry_after}s.",
            },
        )

    # Cache disabled: original synchronous inline behavior, unchanged.
    job_data = await fetch_job_data(settings.JOB_SERVICE_URL, job_id, settings.JOB_SERVICE_TIMEOUT)
    if job_data is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"Failed to fetch job details for job {job_id} from job-service.",
        )

    cv_rank_request, idx_to_username, reason, embeddings_by_index = build_cv_rank_request(
        job_id, store, job_data, options
    )
    if cv_rank_request is None:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=reason)

    logger.info(
        "rank-by-job %s: %d applicants in pool, %d with CV data",
        job_id, len(usernames), len(idx_to_username),
    )

    # Payload stored: dict with job_overview, total_candidates, ranked_applicants
    # ranked_applicants has usernames already resolved — avoids idx staleness risk.
    def _compute() -> dict:
        result = rank_resumes(pipeline, cv_rank_request, embeddings_by_index)
        applicants = [
            RankedApplicantResponse(
                username=idx_to_username.get(r["resume_index"], "unknown"),
                score=r["score"],
                similarity_score=r["similarity_score"],
                cross_score=r["cross_score"],
                label=r["label"],
                explanation=r["explanation"],
                match_points=r.get("match_points", []),
                miss_points=r.get("miss_points", []),
                input_coverage=r.get("input_coverage", 1.0),
                fields_used=r.get("fields_used", {}),
            )
            for r in result["ranked_resumes"]
        ]
        return {
            "job_overview": result["job_overview"],
            "total_candidates": result["total_candidates"],
            "ranked_applicants": applicants,
        }

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
