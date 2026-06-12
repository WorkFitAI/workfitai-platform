"""
CV Ranking API routes.

POST /api/v1/cv-ranking/rank          — raw request (caller supplies job + resumes)
POST /api/v1/cv-ranking/rank-by-job/{job_id} — HR button: uses Kafka-synced store
"""

import logging
from typing import Optional

import httpx
from fastapi import APIRouter, Body, HTTPException, Request, status

from app.models.cv_rank_requests import CvRankRequest, JobRequest, OptionsRequest, ResumeRequest
from app.models.cv_rank_responses import (
    CvRankByJobResponse,
    CvRankResponse,
    RankedApplicantResponse,
    RankedResumeResponse,
)
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

    - Applicant list is maintained by CvReferConsumer (application-events)
    - CV data is maintained by CvReferConsumer (cv.updated)
    - Job data is fetched once from job-service

    Returns 404 when job has no applicants in the ranking pool (APPLIED / REVIEWING / INTERVIEW).
    Returns 503 when pipeline is not ready or CV data is missing for applicants.
    """
    from app.config import get_settings

    pipeline = req.app.state.cv_ranking_pipeline()
    store = req.app.state.cv_refer_store()

    if pipeline is None or not pipeline.is_ready:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="CV ranking pipeline is not ready. Model weights are missing.",
        )

    # 1. Get applicant usernames from store
    usernames = store.get_applicant_usernames(job_id)
    if not usernames:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"No active applicants in ranking pool for job {job_id}.",
        )

    # 2. Build ResumeRequest list — skip applicants whose CV data hasn't synced yet
    resumes = []
    missing_cv = []
    idx_to_username: dict[int, str] = {}   # maps resume_index → username for result enrichment
    for idx, username in enumerate(usernames):
        cv = store.get_cv_data(username)
        if cv is None:
            missing_cv.append(username)
            continue
        idx_to_username[idx] = username
        resumes.append(ResumeRequest(
            resume_index=idx,
            resume_summary=cv.get("resumeSummary", ""),
            resume_experience=cv.get("resumeExperience", ""),
            resume_skills=cv.get("resumeSkills", ""),
            resume_education=cv.get("resumeEducation", ""),
        ))

    if missing_cv:
        logger.warning(
            "rank-by-job %s: CV data not yet synced for %d/%d applicants: %s",
            job_id, len(missing_cv), len(usernames), missing_cv[:5],
        )

    if not resumes:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="CV data has not been synced yet for this job's applicants. Retry shortly.",
        )

    # 3. Fetch job data from job-service (one call per ranking request — acceptable)
    settings = get_settings()
    job_data = await _fetch_job_data(settings.JOB_SERVICE_URL, job_id, settings.JOB_SERVICE_TIMEOUT)
    if job_data is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"Failed to fetch job details for job {job_id} from job-service.",
        )

    job_request = JobRequest(
        job_index=0,
        jd_overview=job_data.get("shortDescription") or job_data.get("title", ""),
        jd_requirements=job_data.get("requirements", ""),
        jd_responsibilities=job_data.get("responsibilities", ""),
        jd_preferred=job_data.get("benefits", ""),
        job_description_text=job_data.get("description", ""),
    )

    # 4. Run pipeline
    cv_rank_request = CvRankRequest(
        job=job_request,
        resumes=resumes,
        options=options or OptionsRequest(),
    )

    logger.info(
        "rank-by-job %s: ranking %d/%d applicants (missing CV: %d)",
        job_id, len(resumes), len(usernames), len(missing_cv),
    )

    try:
        result = rank_resumes(pipeline, cv_rank_request)
    except RuntimeError as e:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(e))
    except Exception as e:
        logger.error("rank-by-job %s failed: %s", job_id, e, exc_info=True)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=str(e))

    ranked_applicants = [
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

    logger.info(
        "rank-by-job %s complete: ranked=%d/%d time=%.1fms",
        job_id, len(ranked_applicants), result["total_candidates"], result["processing_time_ms"],
    )

    return CvRankByJobResponse(
        job_id=job_id,
        job_overview=result["job_overview"],
        total_candidates=result["total_candidates"],
        ranked_count=len(ranked_applicants),
        processing_time_ms=result["processing_time_ms"],
        ranked_applicants=ranked_applicants,
    )


async def _fetch_job_data(job_service_url: str, job_id: str, timeout: int) -> Optional[dict]:
    """Fetch job details from job-service. Returns None on failure."""
    url = f"{job_service_url}/api/v1/public/jobs/{job_id}"
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            resp = await client.get(url)
            resp.raise_for_status()
            body = resp.json()
            # job-service wraps response in { data: {...} } or returns directly
            return body.get("data", body)
    except Exception as e:
        logger.error("Failed to fetch job %s from %s: %s", job_id, url, e)
        return None
