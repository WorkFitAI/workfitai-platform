"""
Internal-only routes for cv-refer state reconciliation.

Not routed through API Gateway — accessible only within the Docker network.
Used by application-service's CvSnapshotReconciliationService to push a
backfilled CV snapshot straight into CvReferStore right after a successful
reparse, instead of waiting for the next full startup resync.
"""

import logging

from fastapi import APIRouter, Request
from pydantic import BaseModel

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/internal/cv-refer", tags=["CV Refer Internal"])


class CvSnapshotPushRequest(BaseModel):
    job_id: str
    username: str
    summary: str = ""
    experience: str = ""
    skills: str = ""
    education: str = ""


@router.post("/snapshot")
async def push_cv_snapshot(request: CvSnapshotPushRequest, req: Request) -> dict:
    """
    Backfill a single applicant's CV snapshot in the in-memory store and mark the
    job's cached ranking dirty so the next rank-by-job call (or the background
    refresher) picks up the fix.
    """
    store = req.app.state.cv_refer_store()
    store.add_applicant(request.job_id, request.username)
    snapshot = {
        "summary": request.summary,
        "experience": request.experience,
        "skills": request.skills,
        "education": request.education,
    }
    store.set_cv_snapshot(request.job_id, request.username, snapshot)
    logger.info(
        "cv-refer-internal: snapshot pushed via reconciliation — jobId=%s username=%s",
        request.job_id, request.username,
    )

    pipeline = req.app.state.cv_ranking_pipeline()
    if pipeline is not None and pipeline.is_ready and any(snapshot.values()):
        try:
            from src.inference.pipeline import ResumeInput
            resume = ResumeInput(
                resume_index=0,
                resume_summary=request.summary,
                resume_experience=request.experience,
                resume_skills=request.skills,
                resume_education=request.education,
            )
            embedding = pipeline.encode_resume(resume)
            store.set_cv_embedding(request.job_id, request.username, embedding)
        except Exception as exc:
            logger.warning(
                "cv-refer-internal: failed to precompute embedding for %s/%s: %s",
                request.job_id, request.username, exc,
            )

    try:
        from app.config import get_settings
        if get_settings().CV_RANKING_CACHE_ENABLED:
            from app.services.cv_ranking_cache import get_cv_ranking_cache
            get_cv_ranking_cache().mark_dirty(request.job_id)

            refresher = req.app.state.cv_ranking_refresher()
            if refresher is not None:
                refresher.trigger_immediate(request.job_id)
    except Exception as exc:
        logger.warning("cv-refer-internal: failed to mark ranking dirty for job %s: %s", request.job_id, exc)

    return {"status": "ok"}
