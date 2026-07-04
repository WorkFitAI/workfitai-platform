"""
Shared helper for building a CvRankRequest from store + job-service data.

Used by both the rank-by-job endpoint and the CvRankingRefresher (Phase 3)
so request assembly logic is not duplicated (DRY).
"""

import logging
from typing import Dict, Optional, Tuple

import httpx

from app.models.cv_rank_requests import CvRankRequest, JobRequest, OptionsRequest, ResumeRequest
from app.services.cv_refer_store import CvReferStore

logger = logging.getLogger(__name__)


async def fetch_job_data(job_service_url: str, job_id: str, timeout: int) -> Optional[Dict]:
    """Fetch job details from job-service. Returns None on any failure."""
    url = f"{job_service_url}/public/jobs/{job_id}"
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            resp = await client.get(url)
            resp.raise_for_status()
            body = resp.json()
            return body.get("data", body)
    except Exception as exc:
        logger.error("Failed to fetch job %s from %s: %s", job_id, url, exc)
        return None


def build_cv_rank_request(
    job_id: str,
    store: CvReferStore,
    job_data: Dict,
    options: Optional[OptionsRequest] = None,
) -> Tuple[Optional[CvRankRequest], Optional[Dict[int, str]], str, Dict[int, object]]:
    """
    Build a CvRankRequest from store state and pre-fetched job_data.

    Returns:
        (request, idx_to_username, reason, embeddings_by_index)
        On failure: (None, None, human-readable reason string, {})

    idx_to_username maps resume_index → username so pipeline results can be
    translated back to applicant usernames by the caller.

    embeddings_by_index maps resume_index → precomputed bi-encoder embedding
    (CvReferStore's cache, set when the snapshot was written) — entries missing
    here just get encoded on the fly by the pipeline, same as before this cache
    existed.
    """
    usernames = store.get_applicant_usernames(job_id)
    if not usernames:
        return None, None, f"No active applicants in ranking pool for job {job_id}.", {}

    resumes: list[ResumeRequest] = []
    idx_to_username: Dict[int, str] = {}
    embeddings_by_index: Dict[int, object] = {}
    missing_cv: list[str] = []

    for idx, username in enumerate(usernames):
        cv = store.get_cv_data(job_id, username)
        if cv is None:
            missing_cv.append(username)
            continue
        idx_to_username[idx] = username
        resumes.append(ResumeRequest(
            resume_index=idx,
            resume_summary=cv.get("summary", ""),
            resume_experience=cv.get("experience", ""),
            resume_skills=cv.get("skills", ""),
            resume_education=cv.get("education", ""),
        ))
        embedding = store.get_cv_embedding(job_id, username)
        if embedding is not None:
            embeddings_by_index[idx] = embedding

    if missing_cv:
        logger.warning(
            "cv-rank-assembly job=%s: CV data missing for %d/%d applicants: %s",
            job_id, len(missing_cv), len(usernames), missing_cv[:5],
        )

    if not resumes:
        return None, None, "CV data has not been synced yet for this job's applicants.", {}

    job_request = JobRequest(
        job_index=0,
        jd_overview=job_data.get("shortDescription") or job_data.get("title", ""),
        jd_requirements=job_data.get("requirements", ""),
        jd_responsibilities=job_data.get("responsibilities", ""),
        jd_preferred=job_data.get("benefits", ""),
        job_description_text=job_data.get("description", ""),
    )

    request = CvRankRequest(
        job=job_request,
        resumes=resumes,
        options=options or OptionsRequest(),
    )
    return request, idx_to_username, "", embeddings_by_index
