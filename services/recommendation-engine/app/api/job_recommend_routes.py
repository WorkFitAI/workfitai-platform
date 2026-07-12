"""
API route handlers for recommendation endpoints
"""

import base64
import time
import logging
from typing import List, Dict, Optional

import anyio
import numpy as np
from fastapi import APIRouter, HTTPException, Request, status

from app.models.job_recommend_requests import (
    RecommendByResumeRequest,
    RecommendByProfileRequest,
    SimilarJobsRequest,
    SemanticSearchRequest
)
from app.models.job_recommend_responses import (
    RecommendationResponse,
    RecommendationData,
    JobRecommendation
)
from app.services.field_format import JOB_FIELDS, RESUME_FIELDS

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/v1/recommendations", tags=["Recommendations"])


async def _ensure_job_recommendation_enabled(req: Request, candidate_username: Optional[str] = None) -> None:
    """
    Raise 503 if job-recommendation AI is disabled.

    Always checks the admin platform-wide toggle. When candidate_username is
    supplied (only by-profile carries one today), additionally ANDs with that
    candidate's personal consent (aiJobRecommendationEnabled).
    """
    store = req.app.state.feature_toggle_store()
    if store is not None and not store.get("job-recommendation"):
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="AI job recommendations are currently disabled by administrator.",
        )

    if candidate_username:
        from app.config import get_settings
        from app.services.ai_consent_client import fetch_ai_job_recommendation_consent

        settings = get_settings()
        consent = await fetch_ai_job_recommendation_consent(
            settings.USER_SERVICE_URL, candidate_username, settings.INTERNAL_SERVICE_TIMEOUT
        )
        if not consent:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="AI job recommendations are disabled for this candidate.",
            )


def _extract_company_name(result: Dict) -> str:
    """Extract company name from result metadata"""
    company = result.get('company')
    if isinstance(company, dict):
        return company.get('companyName', 'Unknown')
    elif isinstance(company, str):
        return company
    return 'Unknown'


def _prepare_filters(request_filters) -> Dict:
    """Convert request filters to FAISS search filters"""
    filters = {}
    if not request_filters:
        return filters

    if request_filters.locations and len(request_filters.locations) > 0:
        filters['location'] = request_filters.locations[0]
    if request_filters.minSalary:
        filters['minSalary'] = request_filters.minSalary
    if request_filters.maxSalary:
        filters['maxSalary'] = request_filters.maxSalary
    if request_filters.experienceLevels and len(request_filters.experienceLevels) > 0:
        filters['experienceLevel'] = request_filters.experienceLevels[0]
    if request_filters.employmentTypes and len(request_filters.employmentTypes) > 0:
        filters['jobType'] = request_filters.employmentTypes[0]

    return filters


def _create_job_recommendation(result: Dict, rank: int) -> JobRecommendation:
    """Create JobRecommendation from FAISS search result"""
    score = min(1.0, max(0.0, float(result['score'])))
    return JobRecommendation(
        jobId=result['jobId'],
        score=score,
        rank=rank,
        title=result.get('title', 'Unknown'),
        company=_extract_company_name(result),
        location=result.get('location', 'Unknown'),
        salary=result.get('salary'),
        experienceLevel=result.get('experienceLevel'),
        jobType=result.get('jobType'),
        skills=result.get('skills', [])
    )


def _cosine(a: np.ndarray, b: np.ndarray) -> float:
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b) + 1e-8))


def _add_transparency_payload(
    rec: JobRecommendation,
    pooled_resume_emb: np.ndarray,
    resume_field_embs: Dict[str, np.ndarray],
    resume_mask: Dict[str, bool],
    faiss_manager,
) -> None:
    """
    Mutates `rec` in place with a per-field similarity breakdown and a
    natural-language explanation, mirroring job-recomendation/source/pipeline.py's
    recommend_multifield() design: each present resume field is compared
    against the mean of the job's present field embeddings, and each present
    job field is compared against the resume's actual pooled query embedding
    (not a freshly-recomputed resume mean -- pooled_resume_emb already is
    that, from EmbeddingGenerator.encode_resume_fields()).
    """
    job_field_embs = faiss_manager.get_job_field_embeddings(rec.jobId) or {}
    resume_present = [name for name, present in resume_mask.items() if present]
    job_present = list(job_field_embs.keys())

    per_field_similarity: Dict[str, float] = {}
    if job_field_embs:
        job_mean = np.mean(list(job_field_embs.values()), axis=0)
        for name in resume_present:
            per_field_similarity[f"resume:{name}"] = _cosine(resume_field_embs[name], job_mean)
    for name, emb in job_field_embs.items():
        per_field_similarity[f"job:{name}"] = _cosine(pooled_resume_emb, emb)

    rec.resumeFieldsPresent = resume_present
    rec.jobFieldsPresent = job_present
    rec.perFieldSimilarity = per_field_similarity
    rec.fieldsUnavailable = {
        "resume": [name for name, present in resume_mask.items() if not present],
        "job": [name for name in JOB_FIELDS if name not in job_present],
    }

    top_fields = sorted(per_field_similarity.items(), key=lambda kv: kv[1], reverse=True)[:2]
    if top_fields:
        rec.matchReasons = ["Top contributing fields: " + ", ".join(name for name, _ in top_fields)]


# ---------------------------------------------------------------------------
# by-resume
# ---------------------------------------------------------------------------

@router.post("/by-resume", response_model=RecommendationResponse)
async def recommend_by_resume(request: RecommendByResumeRequest, req: Request):
    """
    Get job recommendations based on resume PDF.

    - **resumeFile**: Base64 encoded PDF file
    - **topK**: Number of recommendations (1-100)
    - **filters**: Optional filters for location, salary, etc.

    Cache key: SHA-256 of (formatted resume text, filters, topK).
    PDF parsing runs before cache check; embedding+FAISS is cached.
    """
    from app.config import get_settings
    from app.services.recommendation_cache import RecommendationCache, get_recommendation_cache

    start_time = time.time()
    settings = get_settings()

    faiss_manager = req.app.state.faiss_manager()
    embedding_generator = req.app.state.embedding_generator()
    resume_parser = req.app.state.resume_parser()

    if not faiss_manager or not embedding_generator or not resume_parser:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Service not ready")

    await _ensure_job_recommendation_enabled(req)

    logger.info("Recommend by resume (base64 length: %d, topK=%d)", len(request.resumeFile), request.topK)

    try:
        pdf_bytes = base64.b64decode(request.resumeFile)
    except Exception as exc:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Invalid base64 encoding: {exc}")

    try:
        parsed_data = resume_parser.parse_resume(pdf_bytes)
        if not parsed_data:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Failed to parse resume")
    except HTTPException:
        raise
    except Exception as exc:
        logger.error("Resume parsing error: %s", exc, exc_info=True)
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Resume parsing failed: {exc}")

    # Key computed after parse (cache saves embedding + FAISS, not PDF parse)
    formatted_text = resume_parser.format_resume_for_matching(parsed_data)
    filters = _prepare_filters(request.filters)

    def _compute() -> List[JobRecommendation]:
        query_embedding = embedding_generator.encode_resume(formatted_text)
        results = faiss_manager.search(query_embedding=query_embedding, top_k=request.topK, filters=filters)
        return [_create_job_recommendation(r, i + 1) for i, r in enumerate(results)]

    if settings.RECOMMENDATION_CACHE_ENABLED:
        cache = get_recommendation_cache()
        key = RecommendationCache.key_for_resume(formatted_text, filters, request.topK)
        cooldown = float(settings.RECOMMENDATION_CACHE_COOLDOWN_SECONDS)
        recommendations, was_hit = await anyio.to_thread.run_sync(
            lambda: cache.get_or_compute(key, _compute, cooldown)
        )
        cache.maybe_refresh(key, _compute, cooldown)
        logger.info("by-resume: cache=%s results=%d", "hit" if was_hit else "miss", len(recommendations))
    else:
        recommendations = await anyio.to_thread.run_sync(_compute)

    processing_time = f"{int((time.time() - start_time) * 1000)}ms"
    logger.info("✓ Found %d matching jobs in %s", len(recommendations), processing_time)

    return RecommendationResponse(
        success=True,
        data=RecommendationData(
            totalResults=len(recommendations),
            recommendations=recommendations,
            processingTime=processing_time
        ),
        message=f"Found {len(recommendations)} matching jobs"
    )


# ---------------------------------------------------------------------------
# by-profile
# ---------------------------------------------------------------------------

@router.post("/by-profile", response_model=RecommendationResponse)
async def recommend_by_profile(request: RecommendByProfileRequest, req: Request):
    """
    Get job recommendations based on text profile.

    Pipeline:
    1. Bi-encoder retrieves top-K candidates from FAISS (fast)
    2. Cross-encoder reranks to top-N (accurate) — if enabled

    - **profileText**: User profile text (skills, experience)
    - **topK**: Number of final recommendations (default: 20)
    - **filters**: Optional filters
    """
    from app.config import get_settings
    from app.services.recommendation_cache import RecommendationCache, get_recommendation_cache

    start_time = time.time()
    settings = get_settings()

    faiss_manager = req.app.state.faiss_manager()
    embedding_generator = req.app.state.embedding_generator()
    reranker = req.app.state.reranker()

    if not faiss_manager or not embedding_generator:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Service not ready")

    await _ensure_job_recommendation_enabled(req, request.candidateUsername)

    logger.info("Recommend by profile (text length: %d, topK=%d)", len(request.profileText), request.topK)

    filters = _prepare_filters(request.filters)
    retrieve_k = settings.RERANK_TOP_K if reranker else request.topK

    # Multi-field mode activates only when the caller supplies at least one
    # structured field -- profileText-only requests behave exactly as before
    # (encode_resume, no transparency payload), fully backward compatible.
    structured_fields = {
        "resume_text": request.profileText,
        "resume_summary": request.resumeSummary or "",
        "resume_experience": request.resumeExperience or "",
        "resume_skills": request.resumeSkills or "",
        "resume_education": request.resumeEducation or "",
    }
    use_multi_field = any(
        [request.resumeSummary, request.resumeExperience, request.resumeSkills, request.resumeEducation]
    )

    def _compute() -> List[JobRecommendation]:
        if use_multi_field:
            pooled_resume_emb, resume_field_embs, resume_mask_list = embedding_generator.encode_resume_fields(
                structured_fields, RESUME_FIELDS
            )
            resume_mask = dict(zip(RESUME_FIELDS, resume_mask_list))
            query_embedding = pooled_resume_emb
        else:
            query_embedding = embedding_generator.encode_resume(request.profileText)

        results = faiss_manager.search(query_embedding=query_embedding, top_k=retrieve_k, filters=filters)
        candidates = [_create_job_recommendation(r, i + 1) for i, r in enumerate(results)]

        if use_multi_field:
            for candidate in candidates:
                _add_transparency_payload(
                    candidate, pooled_resume_emb, resume_field_embs, resume_mask, faiss_manager
                )

        if reranker and candidates:
            try:
                # JobRecommendation only carries display metadata (title,
                # company-as-string, location, skills, ...) -- the cross-
                # encoder needs the raw text fields (description,
                # shortDescription, requirements, responsibilities, benefits)
                # too, which only the FAISS search result metadata has.
                raw_by_job_id = {r['jobId']: r for r in results}
                candidates_dict = []
                for c in candidates:
                    d = c.model_dump() if hasattr(c, 'model_dump') else c
                    raw = raw_by_job_id.get(d.get('jobId'), {})
                    d['description'] = raw.get('description', '')
                    d['shortDescription'] = raw.get('shortDescription', '')
                    d['requirements'] = raw.get('requirements', '')
                    d['responsibilities'] = raw.get('responsibilities', '')
                    d['benefits'] = raw.get('benefits', '')
                    candidates_dict.append(d)

                reranked = reranker.rerank(
                    resume_text=request.profileText,
                    candidates=candidates_dict,
                    top_n=request.topK,
                    resume_fields=structured_fields if use_multi_field else None,
                )
                return [JobRecommendation(**r) for r in reranked]
            except Exception as exc:
                logger.error("Reranking failed, using bi-encoder results: %s", exc)
                return candidates[:request.topK]
        return candidates[:request.topK]

    if settings.RECOMMENDATION_CACHE_ENABLED:
        cache = get_recommendation_cache()
        key = RecommendationCache.key_for_profile(
            request.profileText, filters, request.topK,
            structured_fields=structured_fields if use_multi_field else None,
        )
        cooldown = float(settings.RECOMMENDATION_CACHE_COOLDOWN_SECONDS)
        recommendations, was_hit = await anyio.to_thread.run_sync(
            lambda: cache.get_or_compute(key, _compute, cooldown)
        )
        cache.maybe_refresh(key, _compute, cooldown)
        logger.info("by-profile: cache=%s results=%d", "hit" if was_hit else "miss", len(recommendations))
    else:
        recommendations = await anyio.to_thread.run_sync(_compute)

    processing_time = f"{int((time.time() - start_time) * 1000)}ms"
    logger.info("✓ Found %d matching jobs in %s", len(recommendations), processing_time)

    return RecommendationResponse(
        success=True,
        data=RecommendationData(
            totalResults=len(recommendations),
            recommendations=recommendations,
            processingTime=processing_time
        ),
        message=f"Found {len(recommendations)} matching jobs"
    )


# ---------------------------------------------------------------------------
# similar-jobs
# ---------------------------------------------------------------------------

@router.post("/similar-jobs", response_model=RecommendationResponse)
async def find_similar_jobs(request: SimilarJobsRequest, req: Request):
    """
    Find jobs similar to a reference job.

    - **jobId**: Reference job ID
    - **topK**: Number of similar jobs
    - **excludeSameCompany**: Exclude jobs from same company
    """
    from app.config import get_settings
    from app.services.recommendation_cache import RecommendationCache, get_recommendation_cache

    start_time = time.time()
    settings = get_settings()

    faiss_manager = req.app.state.faiss_manager()
    if not faiss_manager:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Service not ready")

    await _ensure_job_recommendation_enabled(req)

    logger.info(
        "Finding similar jobs for %s (topK=%d, excludeSameCompany=%s)",
        request.jobId, request.topK, request.excludeSameCompany
    )

    # 404 guard before any cache check
    job_data = faiss_manager.get_job_by_id(request.jobId)
    if not job_data:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Job {request.jobId} not found in index"
        )

    reference_company = _extract_company_name(job_data)
    query_embedding = job_data['embedding']

    def _compute() -> List[JobRecommendation]:
        results = faiss_manager.search(
            query_embedding=query_embedding,
            top_k=request.topK + 1,
            filters={}
        )
        recommendations: List[JobRecommendation] = []
        for result in results:
            if result['jobId'] == request.jobId:
                continue
            if request.excludeSameCompany and _extract_company_name(result) == reference_company:
                continue
            recommendations.append(_create_job_recommendation(result, len(recommendations) + 1))
            if len(recommendations) >= request.topK:
                break
        return recommendations

    if settings.RECOMMENDATION_CACHE_ENABLED:
        cache = get_recommendation_cache()
        key = RecommendationCache.key_for_similar(request.jobId, request.topK, request.excludeSameCompany)
        cooldown = float(settings.RECOMMENDATION_CACHE_COOLDOWN_SECONDS)
        recommendations, was_hit = await anyio.to_thread.run_sync(
            lambda: cache.get_or_compute(key, _compute, cooldown)
        )
        cache.maybe_refresh(key, _compute, cooldown)
        logger.info("similar-jobs: cache=%s results=%d", "hit" if was_hit else "miss", len(recommendations))
    else:
        recommendations = await anyio.to_thread.run_sync(_compute)

    processing_time = f"{int((time.time() - start_time) * 1000)}ms"
    logger.info("✓ Found %d similar jobs in %s", len(recommendations), processing_time)

    return RecommendationResponse(
        success=True,
        data=RecommendationData(
            totalResults=len(recommendations),
            recommendations=recommendations,
            processingTime=processing_time
        ),
        message=f"Found {len(recommendations)} similar jobs"
    )


# ---------------------------------------------------------------------------
# search
# ---------------------------------------------------------------------------

@router.post("/search", response_model=RecommendationResponse)
async def semantic_search(request: SemanticSearchRequest, req: Request):
    """
    Semantic search for jobs using text query.

    - **query**: Search query text
    - **topK**: Number of results
    - **filters**: Optional filters
    """
    from app.config import get_settings
    from app.services.recommendation_cache import RecommendationCache, get_recommendation_cache

    start_time = time.time()
    settings = get_settings()

    faiss_manager = req.app.state.faiss_manager()
    embedding_generator = req.app.state.embedding_generator()

    if not faiss_manager or not embedding_generator:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Service not ready")

    await _ensure_job_recommendation_enabled(req)

    logger.info("Semantic search: '%.50s...' topK=%d", request.query, request.topK)

    filters = _prepare_filters(request.filters)

    def _compute() -> List[JobRecommendation]:
        query_embedding = embedding_generator.encode_resume(request.query)
        results = faiss_manager.search(query_embedding=query_embedding, top_k=request.topK, filters=filters)
        return [_create_job_recommendation(r, i + 1) for i, r in enumerate(results)]

    if settings.RECOMMENDATION_CACHE_ENABLED:
        cache = get_recommendation_cache()
        key = RecommendationCache.key_for_search(request.query, filters, request.topK)
        cooldown = float(settings.RECOMMENDATION_CACHE_COOLDOWN_SECONDS)
        recommendations, was_hit = await anyio.to_thread.run_sync(
            lambda: cache.get_or_compute(key, _compute, cooldown)
        )
        cache.maybe_refresh(key, _compute, cooldown)
        logger.info("search: cache=%s results=%d", "hit" if was_hit else "miss", len(recommendations))
    else:
        recommendations = await anyio.to_thread.run_sync(_compute)

    processing_time = f"{int((time.time() - start_time) * 1000)}ms"
    logger.info("✓ Found %d matching jobs in %s", len(recommendations), processing_time)

    return RecommendationResponse(
        success=True,
        data=RecommendationData(
            totalResults=len(recommendations),
            recommendations=recommendations,
            processingTime=processing_time
        ),
        message=f"Found {len(recommendations)} matching jobs"
    )


# ---------------------------------------------------------------------------
# Admin
# ---------------------------------------------------------------------------

@router.post("/admin/sync", tags=["Admin"])
async def trigger_sync(req: Request):
    """Trigger manual sync from Job Service (Admin endpoint)"""
    start_time = time.time()

    faiss_manager = req.app.state.faiss_manager()
    embedding_generator = req.app.state.embedding_generator()

    if not faiss_manager or not embedding_generator:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Service not ready")

    logger.info("Manual sync triggered by admin")

    from app.services.job_sync import sync_jobs_from_service
    synced_count = await sync_jobs_from_service(faiss_manager, embedding_generator)

    processing_time = f"{int((time.time() - start_time) * 1000)}ms"
    return {
        "success": True,
        "message": f"Successfully synced {synced_count} jobs",
        "jobsSynced": synced_count,
        "processingTime": processing_time
    }
